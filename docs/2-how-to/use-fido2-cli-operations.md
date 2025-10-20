# Use the FIDO2/WebAuthn CLI

_Status: Draft_  
_Last updated: 2025-10-18_

The `fido2` Picocli facade lets you validate WebAuthn assertions and attestation payloads against the simulator without touching the REST or UI layers. This guide covers stored-credential evaluations, inline verification, attestation verification (with optional trust anchors), replay diagnostics, and the shared JSON vector presets that keep every facade aligned.

## Prerequisites
- Java 17 (`JAVA_HOME` must point to a JDK 17 install).
- Build dependencies installed via Gradle (`./gradlew spotlessApply check` should already pass).
- Optional but recommended: start the REST API (`./gradlew :rest-api:bootRun`) so you can seed sample credentials through `POST /api/v1/webauthn/credentials/seed` or the operator console. The CLI opens the same MapDB file (`data/credentials.db` by default) that the REST/UI layers use; pass `--database` if you need to point at a legacy file such as `data/fido2-credentials.db`.
- Base64url utilities (`base64url`-style encodings) for supplying raw credential material when you are not using presets.
- (Optional) `jq` or similar tooling if you want to explore the assertion fixtures in `docs/webauthn_assertion_vectors.json` and the attestation fixtures in `docs/webauthn_attestation/*.json`.

Run CLI commands from the repository root. Set `GRADLE_USER_HOME=$PWD/.gradle` if you prefer to isolate Gradle caches in the workspace.

## Generate a Stored WebAuthn Assertion
Stored mode relies on a credential that already lives in MapDB and a private key you control. Presets from `WebAuthnGeneratorSamples` keep the CLI aligned with the operator UI and REST facades.

1. Seed the canonical fixtures if you do not have any WebAuthn credentials yet:
   ```bash
   curl -s -X POST http://localhost:8080/api/v1/webauthn/credentials/seed | jq
   ```
   The response enumerates which credential IDs were added (for example `packed-es256`).
2. Invoke the CLI with a preset. The command below loads the curated challenge + private key for the credential named `packed-es256`, generates a signed `PublicKeyCredential`, prints it to stdout, and appends a sanitized telemetry line:
   ```bash
   ./gradlew --quiet :cli:run --args=$'fido2 evaluate --preset-id packed-es256'
   ```
   Output (truncated):
   ```json
   {
     "id": "V2V...",
     "rawId": "V2V...",
     "type": "public-key",
     "response": {
       "clientDataJSON": "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0IiwgLi4u",
       "authenticatorData": "o3mm9u6vuaVe...",
       "signature": "MEUCIQCMYUI38KCW..."
     },
     "relyingPartyId": "example.org",
     "origin": "https://example.org",
     "algorithm": "ES256",
     "userVerificationRequired": false,
     "signatureCounter": 0
   }
   event=cli.fido2.evaluate status=success credentialSource=stored credentialReference=true algorithm=ES256 origin=https://example.org telemetryId=cli-fido2-64d6c5f9-…
   ```
   The preset catalogue now covers every supported algorithm. Use `packed-es384`,
   `packed-es512`, `packed-rs256`, `synthetic-ps256-uv0_up1`, or `packed-ed25519` when you need RSA/PS or Ed25519 material; each preset ships with the matching private key JWK.
3. To override any preset value, supply explicit options. For example:
   ```bash
   ./gradlew --quiet :cli:run --args=$'fido2 evaluate \
     --preset-id packed-es256 \
     --challenge $(printf stored-test | openssl base64 -A) \
     --private-key-file path/to/custom-es256-private.jwk'
   ```
   The CLI merges your overrides with the preset metadata before generating the assertion.

## Generate Inline Assertions
Inline mode skips persistence—you provide the credential descriptor, challenge, and private key directly. Presets make it easy to start with realistic defaults.

```bash
./gradlew --quiet :cli:run --args=$'fido2 evaluate-inline \
  --preset-id packed-es256 \
  --credential-name inline-demo \
  --origin https://rp.example'
```

The CLI returns the signed `PublicKeyCredential` JSON followed by a sanitized telemetry frame (`credentialSource=inline`, `credentialReference=false`).

To craft assertions manually, omit `--preset-id` and provide the required parameters:

```bash
./gradlew --quiet :cli:run --args=$'fido2 evaluate-inline \
  --credential-name smoke-test \
  --credential-id $(printf "inline-id" | base64 -w0) \
  --relying-party-id example.org \
  --origin https://example.org \
  --type webauthn.get \
  --algorithm ES256 \
  --signature-counter 0 \
  --user-verification-required false \
  --challenge $(printf inline-test | base64 -w0) \
  --private-key-file path/to/custom-es256-private.jwk'
```

Any validation failure (for example an invalid JWK) exits with status `2` and prints a sanitized `reasonCode` such as `private_key_invalid`.

## Replay Stored Assertions
Replays run the same verification logic without mutating counters—use them for incident response or forensic drills.

```bash
./gradlew --quiet :cli:run --args=$'fido2 replay \
  --vector-id resident-key-es256 \
  --credential-id resident-key-es256'
```

Replay output reports whether the supplied assertion matches the stored credential (`credentialSource=stored`, `match=true`) and surfaces sanitized errors (for example `reason=mismatch`) when verification fails.

## Generate WebAuthn Attestations
Attestation generation accepts fixture identifiers from the JSON bundles in `docs/webauthn_attestation/`. Provide the challenge straight from the dataset and pass the authenticator private keys as JWK or PEM/PKCS#8; the CLI normalises either representation before invoking the generator. The command emits sanitized telemetry that records the format, signing mode, certificate-chain count, and whether custom roots were supplied. Preset runs are the default (`--input-source preset`), so you only need to provide the preset metadata and signing mode.

```bash
vector=$(jq -r '.[0]' docs/webauthn_attestation/packed.json)
export ATTEST_CHALLENGE=$(jq -r '.registration.challenge_b64u' <<<"$vector")
export CREDENTIAL_KEY=$(jq '.key_material.credential_private_key' <<<"$vector")
export ATTEST_KEY=$(jq '.key_material.attestation_private_key' <<<"$vector")
export ATTEST_SERIAL=$(jq -r '.key_material.attestation_cert_serial_b64u' <<<"$vector")

./gradlew --quiet :cli:run --args="fido2 attest \
  --format packed \
  --attestation-id w3c-packed-es256 \
  --relying-party-id example.org \
  --origin https://example.org \
  --challenge $ATTEST_CHALLENGE \
  --credential-private-key \"$CREDENTIAL_KEY\" \
  --attestation-private-key \"$ATTEST_KEY\" \
  --attestation-serial $ATTEST_SERIAL \
  --signing-mode self-signed"
```

The CLI prints only the generated `clientDataJSON` and `attestationObject`; signature inclusion and certificate chain counts now surface exclusively through the trailing telemetry frame (`generationMode=self_signed`, `signatureIncluded=true`, `certificateChainCount=1`, `customRootCount=0`). Swap the `--credential-private-key` / `--attestation-private-key` arguments for decoded PEM payloads or alternate JWKs if you need custom material—the generator canonicalises all supported encodings before validating. Switch to `--signing-mode unsigned` to emit a structural attestation without a signature. To chain your own trust anchors, supply one or more PEM bundles via `--custom-root-file <path>` and select `--signing-mode custom-root`.

Manual mode skips presets entirely. Pass `--input-source manual` and supply the relying party, origin, challenge, and credential private key directly. Signed modes still require an attestation private key plus the certificate serial (Base64URL), and `custom-root` additionally expects at least one `--custom-root-file` entry. When you derive manual inputs from an existing preset, tag the original identifier with `--seed-preset-id <id>` and note edited fields with `--override <field>`; both values flow into telemetry (`overrides`, `seedPresetId`) for downstream audits.

```bash
./gradlew --quiet :cli:run --args=$'fido2 attest \
  --input-source manual \
  --format packed \
  --relying-party-id example.org \
  --origin https://example.org \
  --challenge '"$(jq -r '.registration.challenge' <<<"$vector")"' \
  --credential-private-key "$(jq '.key_material.credential_private_key' <<<"$vector")" \
  --attestation-private-key "$(jq '.key_material.attestation_private_key' <<<"$vector")" \
  --attestation-serial $(jq -r '.key_material.attestation_cert_serial_b64u' <<<"$vector") \
  --signing-mode self-signed'
```

The manual output mirrors the preset flow but the telemetry frame now includes `inputSource=manual`, optional `seedPresetId`, and any `overrides` you recorded.

## Replay Attestation Verification
`fido2 attest-replay` mirrors the attestation verification flow and remains available for deterministic replays and incident drills. Supply the same Base64URL fields and optional trust anchors extracted from the generated payload:

```bash
./gradlew --quiet :cli:run --args="fido2 attest-replay \
  --format packed \
  --attestation-id w3c-packed-es256 \
  --relying-party-id example.org \
  --origin https://example.org \
  --attestation-object $ATTEST_OBJECT \
  --client-data-json $ATTEST_CLIENT_DATA \
  --expected-challenge $ATTEST_CHALLENGE \
  --trust-anchor-file packed-anchor.pem"
```

The replay output reports whether the supplied anchors matched the attestation chain (`anchorTrusted=true/false`) and keeps the telemetry footprint in sync with the REST and UI facades.

## Browse the JSON Vector Catalogue
The `vectors` subcommand prints the full assertion catalogue (`docs/webauthn_assertion_vectors.json`) followed by the attestation datasets from `docs/webauthn_attestation/*.json` so every facade stays in sync:

```bash
./gradlew --quiet :cli:run --args='fido2 vectors'
vectorId        algorithm       uvRequired     relyingPartyId  origin
packed-es256    ES256           false          example.org     https://example.org
resident-key-es256     ES256    true           example.org     https://example.org
rs256-hsm       RS256           false          hsm.example     https://rp.example
…
attestationId   format  algorithm       relyingPartyId  origin                  section
w3c-packed-es256        packed  ES256   example.org     https://example.org     16.1.6
w3c-packed-es384        packed  ES384   example.org     https://example.org     16.1.7
synthetic-android-key   android-key     EdDSA   rp.local        https://rp.local        synthetic
```

Use these `vectorId` values with `fido2 replay` (verification) and for advanced troubleshooting. Attestation entries surface the same identifiers consumed by `fido2 attest`/`attest-replay`, while generator presets (`--preset-id`) drive assertion creation.

## Troubleshooting & Telemetry Tips
- Exit code `2` (`CommandLine.ExitCode.USAGE`) signals validation failures. The telemetry frame (printed to stderr) includes a sanitized `reasonCode` such as `credential_not_found`, `invalid_payload`, or `signature_counter_regressed`.
- Exit code `1` (`SOFTWARE`) indicates an unexpected error (for example the credential store could not be opened). Review the sanitized message and rerun with `--database <path>` if you need a different MapDB file.
- Attestation telemetry surfaces additional hints: `anchorProvided`/`anchorTrusted` show when supplied trust anchors matched; `selfAttestedFallback=true` denotes runs that accepted a self-attested credential; `certificateFingerprint` reports the SHA-256 digest of the first certificate when anchors are provided.
- Add `--database data/custom-fido2.db` to target a non-default store. The option is inherited by every subcommand.
- When debugging inline payloads, run without presets and progressively add fields to isolate malformed Base64URL data. The CLI reports which parameter failed to decode.

Pair this guide with the REST and operator UI how-tos to mirror the same vectors across every facade.

## Inspect JWK Material
Each vector in `docs/webauthn_assertion_vectors.json` carries a `keyPairJwk` payload (public + private components) alongside the COSE public key. Extract it for documentation or interoperability testing:
```bash
python - <<'PYJ'
import json
from pathlib import Path
vectors = json.loads(Path('docs/webauthn_assertion_vectors.json').read_text())
vector = next(v for v in vectors if v['vector_id'] == 'ES256:uv0_up1')
print(json.dumps(vector['key_material']['keyPairJwk'], indent=2))
PYJ
```
This keeps examples compact while avoiding PEM/PKCS#8 material, matching the UI and REST presets that surface key data in COSE or JWK form only.
