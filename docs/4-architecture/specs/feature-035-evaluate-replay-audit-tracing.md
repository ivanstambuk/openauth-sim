# Feature 035 – Evaluate & Replay Audit Tracing

_Status: Accepted_  
_Last updated: 2025-10-29_

## Overview
Introduce a deterministic, operator-facing audit trace for every credential-evaluation workflow across the simulator. When an operator enables verbose tracing for a request, the system must emit a step-by-step account of each cryptographic operation—down to intermediate buffers and bit-level mutations—so humans can study how the algorithm arrived at the final outcome. Traces are ephemeral (bound to the request that generated them) but must be available through all facades (CLI, REST, operator UI) and future protocols without additional infrastructure work.

## Goals
- Produce a detailed cryptographic trace for all evaluate/replay/attest operations spanning HOTP, TOTP, OCRA, and FIDO2 WebAuthn flows, with an extension point for future protocols.
- Provide per-request toggles so operators explicitly opt into verbose tracing (CLI flag, REST request field, UI control) while default behaviour remains unchanged.
- Return trace data through REST responses and surface the same content in the CLI and operator UI without lossy transformations.
- Preserve step ordering and raw values (unredacted secrets, intermediate hashes, bit strings, etc.) to maximise instructional value.
- Keep traces transient—generated on demand and discarded after the response is delivered.

## Non-goals
- Persisting traces beyond the originating request or introducing retention/rotation policies.
- Introducing a global “always verbose” setting; all toggles remain per request/session.
- General-purpose logging refactors or telemetry redaction changes outside the verbose-trace pathway.

## Clarifications
1. 2025-10-22 – Scope includes every current and future credential protocol; initial delivery must cover HOTP, TOTP, OCRA, and all FIDO2 WebAuthn evaluate/replay/attest flows (owner directive).
2. 2025-10-22 – CLI should emit traces to stdout when a `--verbose` (or equivalent) flag is supplied; REST must expose trace data in the response when a verbose field is set on the request; the operator UI should obtain the REST trace and show it in an interactive, terminal-style panel that is hidden by default but can be toggled on demand (owner directive).
3. 2025-10-22 – Traces must include full, unmasked cryptographic details (secrets, hash rounds, bit-level buffers) so humans can understand how each algorithm behaves. Additional protocol-specific detail proposals are welcome if necessary (owner directive).
4. 2025-10-22 – Traces remain ephemeral per invocation and may be discarded immediately after returning to the operator (owner directive).
5. 2025-10-22 – Verbose tracing is activated with per-request toggles (flags, UI controls); no global configuration switch is permitted (owner directive).
6. 2025-10-23 – Verbose trace UI must live inline below the operator forms: keep the “Enable verbose tracing” controls near the bottom of the console and render the trace panel immediately after them (no fixed/overlay behaviour) (owner directive).
7. 2025-10-23 – The trace model must support redaction tiers (`normal`, `educational`, `lab-secrets`) so future increments can dial how much sensitive material is emitted; Feature 035 will expose the existing full-detail behaviour (effectively `educational`) and log the available tiers without implementing toggle surfaces yet (owner directive).
8. 2025-10-23 – When a trace hashes sensitive inputs for auditing (shared secrets, derived keys, password digests, etc.), always compute a SHA-256 digest regardless of the underlying protocol hash family and emit it with a `sha256:` prefix (owner directive).
9. 2025-10-23 – HOTP traces must follow the line-per-field format defined under “HOTP Trace Formatting”: one scalar per line, deterministic ordering, `step.N: <title>` headers, and secrets rendered only as hashes/lengths; support both evaluation (OTP generation) and verification (window scan) flows with the prescribed step breakdown (owner directive).
10. 2025-10-24 – Extend OCRA verification/replay services to emit verbose traces matching the evaluation format so REST and UI facades surface stored/inline replay timelines when `verbose=true` (owner directive).
11. 2025-10-24 – WebAuthn verbose traces must capture validated client data context (expected type/origin, challenge decoding metadata, token binding presence) while relying on existing application/service validation to reject mismatches (owner directive).
12. 2025-10-24 – Canonicalise WebAuthn relying party identifiers (trim, IDNA to ASCII, lower-case) before hashing or persistence, and surface `rpId.canonical`, `rpIdHash.expected`, and `rpIdHash.match` fields in verbose traces for attestation and assertion workflows (owner directive).
13. 2025-10-25 – WebAuthn verbose traces must decode the COSE public key map and surface key metadata (`cose.kty`, `cose.kty.name`, `cose.alg.name`, curve identifiers, and base64url-encoded coordinates/modulus/exponent) alongside the existing `publicKey.cose.hex` field so auditors can inspect structured key material (owner directive).
14. 2025-10-25 – WebAuthn `verify.signature` trace steps must expose algorithm-specific signature details: emit the incoming signature as base64url plus byte-length, derive DER components (`ecdsa.r.hex`, `ecdsa.s.hex`) for ECDSA algorithms, compute a `ecdsa.lowS` flag, surface the active low-S enforcement policy state, and retain the existing `valid` attribute alongside a new `verify.ok` mirror. RSA traces must identify padding/hash (`rsa.padding`, `rsa.hash`) and key size, PS256 must note the RSASSA-PSS salt/hash configuration, and EdDSA traces should surface raw signature base64url plus length. Flag metadata/COSE algorithm mismatches via `error.alg_mismatch` before continuing (owner directive).
15. 2025-10-25 – Compute the RFC 7638 JWK thumbprint for each WebAuthn credential public key and expose it as `publicKey.jwk.thumbprint.sha256` within verbose traces to provide a stable key identifier (owner directive).
16. 2025-10-25 – Operator console must clear the verbose trace panel whenever protocols, evaluation/replay tabs, or inline/stored modes change so traces remain scoped to the initiating request (owner directive; Option B selected).
17. 2025-10-25 – Adopt Option A for WebAuthn extension handling: always record a `parse.extensions` trace step. When `flags.bits.ED = true`, decode authenticator extension data, emit `extensions.present = true` plus the raw CBOR hex, and surface known fields (`ext.credProps.rk`, `ext.credProtect.policy`, `ext.largeBlobKey.b64u`, `ext.hmac-secret`) while logging unknown entries under a generic map. When `flags.bits.ED = false`, surface `extensions.present = false` with empty decoded fields. Assertion and attestation traces must share the decoding helper so all facades present identical extension metadata (owner directive).
18. 2025-10-26 – Stored WebAuthn evaluation traces must populate `construct.command` with the effective `userVerificationRequired` value even when the request omits an override, mirroring the descriptor policy so operators see the enforced requirement (owner directive; Option B selected).
19. 2025-10-26 – WebAuthn assertion *generation* flows (stored and inline) must emit verbose trace steps that decompose the constructed `clientDataJSON`, `authenticatorData`, and signature payload so operators can inspect generation inputs without performing a replay. Continue hashing secrets per Clarification 8 and surface the same metadata across CLI, REST, and UI facades (owner directive).
20. 2025-10-26 – WebAuthn attestation generation must expose parallel verbose trace detail (CBOR attestation object structure, hashed client data, authenticator data/attested credential fields, and signature payload) while preserving hashing rules and making the information available on every facade that already supports verbose tracing (owner directive).
21. 2025-10-26 – Align WebAuthn stored credential selectors with inline sample presets: dropdown entries must sort by the canonical algorithm sequence ES256 → ES384 → ES512 → RS256 → PS256 → EdDSA before applying name/ID fallbacks so operators see consistent ordering across inline and stored flows (Option A, owner directive).

## Requirements
- Define a structured trace model under `core/` that can capture ordered steps, labelled intermediate values, and protocol-specific annotations while remaining extensible for future credential types.
- Update HOTP, TOTP, OCRA, and FIDO2 evaluation/replay/attestation services to populate the trace model when verbose mode is requested, documenting each cryptographic operation (e.g., key derivation, HMAC rounds, signature verification) with raw inputs/outputs.
- Propagate opt-in flags from each facade to the application layer:
  - CLI: add a `--verbose` (or comparable) option per relevant command, emitting the trace to stdout after the primary result while preserving current exit codes.
  - REST: accept a boolean (or enum) verbose field within request DTOs; include a `trace` payload in the JSON response when requested.
  - Operator UI: provide a per-request control (e.g., toggle or checkbox) that requests verbose mode from REST, and render the returned trace in an interactive panel that mimics a terminal log (collapsed by default).
- Expand OCRA verification/replay application services to generate verbose traces (stored and inline workflows), returning them through REST/CLI/UI responses so replay consumers receive the same step-by-step view as evaluation.
- Deliver at least two concrete UI layout proposals for the trace panel (e.g., bottom dock, side drawer) as part of the feature plan and record the accepted option under `## Clarifications` before implementation.
- Maintain ordering between the algorithm’s execution steps and the trace output so users can follow the computation sequentially.
- Ensure traces are never written to disk, logged through telemetry, or otherwise persisted outside their immediate response plumbing.
- Add automated coverage (unit/integration tests per facade) that verifies traces materialise only when verbose mode is enabled and that representative steps appear for each protocol.
- Update operator/user documentation describing how to enable verbose tracing across CLI, REST, and UI channels.
- Define canonical operation identifiers per protocol (e.g., `hotp.evaluate.stored`, `totp.evaluate.inline`, `ocra.evaluate.inline`, `fido2.assertion.evaluate.stored`, `fido2.attestation.verify`) and reuse step identifiers such as `resolve.credential`, `generate.otp`, `verify.attestation`, and `assemble.result` so traces stay comparable across facades.
- Extend the trace envelope to annotate the effective redaction tier (`normal`, `educational`, `lab-secrets`) even though tier toggles will land in a follow-up; Feature 035 continues to emit the full detail set under the `educational` label.
- Enrich each trace step with structured attributes (e.g., `hex`, `base64url`, `int`, `bool`) while preserving the existing human-readable formatting and add spec anchors (for example `spec: rfc4226§5.3`) so operators can jump back to the governing standards.

## Telemetry & Security Considerations
- Verbose traces must bypass existing telemetry redaction; however, they must remain isolated from telemetry sinks to avoid accidental leakage. Document this constraint in module comments and reviewer guidance.
- Confirm that exposing raw secrets aligns with simulator usage expectations; highlight that traces are intended for controlled environments (documentation callout).

## UX Expectations
- UI panel should support scrolling, monospace formatting, and copy-to-clipboard to aid manual analysis.
- When verbose mode is off, the UI must retain current layout with no trace panel overhead.
- CLI output should clearly demarcate the verbose section (e.g., header/footer) to separate it from standard result summaries.

## Dependencies & Considerations
- Existing DTOs and response contracts will expand; coordinate schema updates across CLI/REST/UI tests.
- Trace model should balance expressiveness with performance to avoid significant overhead when verbose mode is active.
- Future protocols must be able to extend the trace model without breaking existing consumers; consider sealed interfaces or builder patterns.

## Trace Content Baseline
- **HOTP (RFC 4226)** – Produce line-per-field, step-numbered traces with the exact structure below. All secrets remain hashed; digits must be ≤9 and counters rendered as unsigned 64-bit big-endian bytes.
  - *Evaluate / generate:*  
    `step.1: normalize.input` (fields: `op`, `alg` with non-standard note when applicable, `digits`, `counter.input`, `secret.format`, `secret.len.bytes`, `secret.sha256`; `spec: rfc4226§5.1`).  
    `step.2: prepare.counter` (`counter.int`, `counter.bytes.big_endian`; `spec: rfc4226§5.1`).  
    `step.3: hmac.compute` (`hash.block_len`, `key.mode`, `key'.sha256`, `ipad.byte`, `opad.byte`, `inner.input`, `inner.hash`, `outer.input`, `hmac.final`; `spec: rfc4226§5.2`).  
    `step.4: truncate.dynamic` (`last.byte`, `offset.nibble`, `slice.bytes`, `slice.bytes[0]_masked`, `dynamic_binary_code.31bit.big_endian`; `spec: rfc4226§5.3`).  
    `step.5: mod.reduce` (`modulus`, `otp.decimal`, `otp.string.leftpad`; `spec: rfc4226§5.4`).  
    `step.6: result` (`output.otp`).  
  - *Verify / window search:*  
    `step.1: normalize.input` (`op`, `alg`, `digits`, `otp.provided`, `counter.hint`, `window`, `secret.len.bytes`, `secret.sha256`; `spec: rfc4226§5.1`).  
    `step.2: search.window` – emit `window.range = [counter.hint-10, counter.hint+10]` and `order = ascending` ahead of the attempt listings, then log `attempt.<counter>.otp` lines (match=false) for non-matching counters and expand the matching attempt inside `-- begin match.derivation --` / `-- end match.derivation --` using the evaluate steps 2–5; `spec: rfc4226§5.4`.  
    `step.3: decision` (`verify.match`, `matched.counter`, `next.expected.counter`).  
    When a match is found, surface the recommended next counter (`matched.counter + 1`) in metadata/telemetry even if the replay request does not mutate persisted state (inline submissions still advertise the incremented value for operator guidance).  
  - CLI/REST/UI renderers must maintain two-space indentation, `name = value` formatting, explicit endianness labels, and lowercase hex (per formatting rules).
- **TOTP (RFC 6238)** – Reuse HOTP steps, preceded by `derive.time-counter` (epoch, step, drift window, computed counter `T`, `rfc6238§4.2`) and `evaluate.window` (enumerated offsets and results, `rfc6238§4.1`). Emit SHA-256 hashes for secrets and highlight algorithm variants (SHA-1/256/512) with spec anchors `rfc6238§1.2`.
- **OCRA (RFC 6287)** – Include `parse.suite` (raw suite, parsed components, `rfc6287§5.1`), `normalize.inputs` (counter/QA/QN/QH handling, PSHA digests, session/timestamp, `rfc6287§5.2`), `assemble.message` (ordered parts with hex, overall SHA-256 hash of the concatenation, `rfc6287§6`), followed by `compute.hmac`, `truncate.dynamic`, and `mod.reduce` mirroring HOTP with anchor `rfc6287§7`; verification/replay traces append `compare.expected` capturing expected vs supplied OTP values and the match decision. Capture a deterministic `parts.count` plus `parts.order` tuple on every `assemble.message` step so operators can confirm message integrity without recomputing segment permutations.
- For every OCRA `assemble.message` step, surface a `len.bytes` attribute for each segment (`segment.N.*.len.bytes`) as well as the concatenated payload (`message.len.bytes`) so operators can validate padding and normalization alongside the hex data (owner directive, 2025-10-24).
- OCRA trace steps must label the HMAC family using the canonical `alg = HMAC-SHA-*` field; the `compute.hmac` step detail should echo the same canonical name and cite both `rfc6287§7` and `rfc2104` for clarity (owner directive, 2025-10-24).
- Dynamic truncation metadata (digest length, slice bytes, pre-mask integer, mask constant, masked value) is documented here for completeness; tier-based filtering responsibilities move to Feature 036.
- **WebAuthn / FIDO2** – Distinguish between attestation (`webauthn§6.4`, CTAP 2 §5) and assertion (`webauthn§7`) while making each step consumable by humans and automation:
  - *Attestation steps*  
    `step.parse.clientData` – include the raw JSON, `clientDataHash.sha256`, `expected.type`, `type.match`, `challenge.b64u`, `challenge.decoded.len`, `origin.expected`, `origin.match`, plus `tokenBinding.status` and `tokenBinding.id` when present.  
    `step.parse.authenticatorData` – emit `rpIdHash.hex`, the canonicalised relying party identifier (`rpId.canonical`), the expected digest (`rpIdHash.expected`), `rpIdHash.match`, `flags.byte` (no `0x` prefix), and the full `flags.bits.{UP,RFU1,UV,BE,BS,RFU2,AT,ED}` map alongside `signCount.int` and any raw counter bytes.  
    `step.extract.attestedCredential` – surface `aaguid`, `credId.b64u`, `credId.len.bytes`, and a structured COSE summary (`cose.kty`, `cose.kty.name`, `cose.alg`, `cose.alg.name`, `cose.crv`, `cose.crv.name`, coordinate or modulus/exponent fields via `cose.x.b64u`, `cose.y.b64u`, `cose.n.b64u`, `cose.e.b64u`) plus `publicKey.cose.hex` and the derived `publicKey.jwk.thumbprint.sha256`.  
    `step.parse.extensions` – honour the Option A directive by always logging `extensions.present`, the raw CBOR hex, decoded well-known fields (`ext.credProps.rk`, `ext.credProtect.policy`, `ext.largeBlobKey.b64u`, `ext.hmac-secret`, etc.), and an `extensions.unknown` map when unfamiliar entries appear.  
    `step.build.signatureBase` – show component lengths (`authenticatorData.len.bytes`, `clientDataHash.len.bytes`, `signedBytes.len.bytes`), keep the concatenated hex via `signedBytes.hex`, and add `signedBytes.preview` (first/last 16 bytes separated by an ellipsis) alongside `signedBytes.sha256`.  
    `step.verify.signature` – report `alg`, `cose.alg`, `verify.sig`, `verify.ok`, and algorithm-specific attributes:  
      • ECDSA (`ES256`, `ES384`, `ES512`): `sig.der.b64u`, `sig.der.len`, `ecdsa.r.hex`, `ecdsa.s.hex`, `ecdsa.lowS`, `policy.lowS.enforced`, and `error.lowS` when enforcement fails.  
      • RSA (`RS256`, `PS256`): `sig.raw.b64u`, `sig.raw.len`, `rsa.padding`, `rsa.hash`, `rsa.pss.salt.len` (when applicable), and `key.bits`.  
      • EdDSA: `sig.raw.b64u`, `sig.raw.len`, plus the raw hex mirror.  
    Flag metadata/COSE mismatches via `error.alg_mismatch` before processing further.  
    `step.validate.metadata` – capture `attestationType` (`Self`, `Basic`, `AttCA`), `trustPath` classification (`none|leaf|chain`), `chain.valid`, `root.anchor`, `aaguid.metadata = known|unknown`, and summarise extension- and policy-related outcomes.
  - *Assertion steps*  
    `step.parse.clientData` and `step.parse.authenticatorData` mirror the attestation fields, followed by `step.parse.extensions`, `step.build.signatureBase`, `step.verify.signature`, and `step.evaluate.counter` (prior counter, new counter, increment verdict). Include `policy.lowS.enforced`/`ecdsa.lowS` status and the same algorithm-specific metadata used for attestation.
  - *Assertion generation (stored & inline evaluate flows)*  
    Extend the existing generation trace (currently `decode.challenge` → `construct.command` → `generate.assertion`) with additional steps shared across CLI/REST/UI:  
    `step.build.clientData` – record resolved type/origin, the Base64URL challenge, decoded length, SHA-256 digest (`clientData.sha256`), and a `clientData.json` preview while omitting raw secrets; hash any underlying JWK/private material before emission.  
    `step.build.authenticatorData` – capture canonicalised relying party ID (`rpId.canonical`), `rpIdHash.expected`, `rpIdHash.hex`, the computed flags byte plus `flags.bits.*`, the chosen signature counter, and toggles such as `userVerificationRequired`.  
    `step.build.signatureBase` – reuse the verification schema (`authenticatorData.len.bytes`, `clientDataHash.len.bytes`, `signedBytes.len.bytes`, `signedBytes.hex`, `signedBytes.preview`, `signedBytes.sha256`) to show exactly what will be signed.  
    `step.generate.signature` – surface algorithm metadata (`alg`, `cose.alg`, `cose.alg.name`), the generated signature in the same encoding that `generate.assertion` returns (DER for ECDSA, raw for EdDSA, etc.), and derived statistics (`sig.der.len`/`sig.raw.len`, `ecdsa.r.hex`, `ecdsa.s.hex`, `rsa.padding`, `rsa.hash`, `key.bits`). Keep a `secret.sha256` placeholder for any private key bytes that influenced the result rather than logging the raw key. These steps must appear for both stored and inline generation so operators never need to rerun replay for inspection.
  - *Attestation generation*  
    Insert parallel steps before `generate.attestation`:  
    `step.build.clientData` (same schema as assertion), `step.build.authenticatorData` (including attested credential data when available: `aaguid`, `credId.b64u`, `credId.len.bytes`, COSE metadata, and `publicKey.jwk.thumbprint.sha256`), `step.compose.attestationObject` (document the CBOR map structure with hashed payload previews: `attObj.cbor.hex`, `attObj.sha256`, `fmt`, `authData.len.bytes`, `attStmt.alg`, certificate chain digest summaries), and `step.build.signatureBase` / `step.generate.signature` mirroring the assertion generation rules. Ensure all sensitive material (private keys, seed secrets) is represented via SHA-256 labels per Clarification 8.
  - Signed-byte reporting must continue to expose full hex plus `signedBytes.sha256` even when previews are supplied, ensuring reproductions remain possible without re-running the operation.
  - When operators toggle between inline/stored, assertion/attestation, or disable verbose mode, facades must clear any previously rendered WebAuthn trace so stale buffers never mingle with the next request.
  - Field naming rules from other protocols still apply: lowercase hex, explicit byte-length attributes, and deterministic indentation with `name = value` formatting so CLI, REST, and UI traces stay machine-friendly.
- **Common presentation** – Every trace lists `operation`, `metadata`, and numbered `steps` with nested key/value lines. Each step should end with an optional `spec:` line referencing the governing section.

## Success Criteria
- All relevant flows expose deterministic, step-by-step traces when verbose mode is enabled and remain unchanged otherwise.
- Automated tests confirm presence/absence of traces across facades and validate key step contents.
- Documentation, knowledge map, and roadmap entries reflect the new verbose tracing capability.
- `./gradlew spotlessApply check` completes successfully after implementation.

## Completion Notes
- 2025-10-22 – Core trace model, application verbose plumbing, CLI flags, and REST verbose payloads shipped with module suites (`./gradlew --no-daemon :core:test`, `:application:test`, `:cli:test`, `:rest-api:test`) covering HOTP, TOTP, OCRA, and WebAuthn flows.
- 2025-10-24 – WebAuthn verbose traces gained canonical metadata (RP ID normalisation, signature inspection, COSE key decoding) alongside refreshed OpenAPI snapshots and Selenium coverage for the operator console.
- 2025-10-26 – Stored credential ordering and UI trace presentation aligned across protocols; full `./gradlew --no-daemon spotlessApply check` confirmed the feature ready for hand-off.
- 2025-10-29 – Specification accepted; tier-control follow-up continues under Feature 036 with verbose tracing fully operational.

## Rollout & Future Work
- Evaluate adding reusable visualisations (e.g., diff views, highlight toggles) once the initial terminal-style panel ships.
- Consider optional exporters (files, web sockets) under future features if persistent audit history becomes desirable.
- Implement opt-in tier controls via the dedicated Feature 036 once the shared helper lands.
