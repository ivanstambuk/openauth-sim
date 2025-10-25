# Feature 035 – Evaluate & Replay Audit Tracing

_Status: Proposed_  
_Last updated: 2025-10-25_

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
14. 2025-10-25 – Compute the RFC 7638 JWK thumbprint for each WebAuthn credential public key and expose it as `publicKey.jwk.thumbprint.sha256` within verbose traces to provide a stable key identifier (owner directive).
15. 2025-10-25 – Operator console must clear the verbose trace panel whenever protocols, evaluation/replay tabs, or inline/stored modes change so traces remain scoped to the initiating request (owner directive; Option B selected).

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
- **OCRA (RFC 6287)** – Include `parse.suite` (raw suite, parsed components, `rfc6287§5.1`), `normalize.inputs` (counter/QA/QN/QH handling, PSHA digests, session/timestamp, `rfc6287§5.2`), `assemble.message` (ordered parts with hex, overall SHA-256 hash of the concatenation, `rfc6287§6`), followed by `compute.hmac`, `truncate.dynamic`, and `mod.reduce` mirroring HOTP with anchor `rfc6287§7`; verification/replay traces append `compare.expected` capturing expected vs supplied OTP values and the match decision.
- For every OCRA `assemble.message` step, surface a `len.bytes` attribute for each segment (`segment.N.*.len.bytes`) as well as the concatenated payload (`message.len.bytes`) so operators can validate padding and normalization alongside the hex data (owner directive, 2025-10-24).
- OCRA trace steps must label the HMAC family using the canonical `alg = HMAC-SHA-*` field; the `compute.hmac` step detail should echo the same canonical name and cite both `rfc6287§7` and `rfc2104` for clarity (owner directive, 2025-10-24).
- Dynamic truncation metadata (digest length, slice bytes, pre-mask integer, mask constant, masked value) is documented here for completeness; tier-based filtering responsibilities move to Feature 036.
- **WebAuthn / FIDO2** – Distinguish between attestation (`webauthn§6.4`, CTAP 2 §5) and assertion (`webauthn§7`):
  - Attestation steps: `parse.clientData` (JSON, SHA-256 hash), `parse.authenticatorData` (RP ID hash, flags map, counters), `extract.attestedCredential` (AAGUID, credential ID, COSE key breakdown), `build.signatureBase` (authData || clientDataHash), `verify.signature` (algorithm, DER/RS values, low-S flag), `validate.metadata` (trust chain, AAGUID lookup). Include extensions when present and note verification outcome.
  - Assertion steps: `parse.clientData`, `parse.authenticatorData`, `build.signatureBase`, `verify.signature`, and `evaluate.counter` (previous vs new counter, strict increment result), plus extension interpretations as applicable.
  - Canonical naming: emit `alg` for signature algorithms (e.g., `ES256`, `ES384`, `ES512`, `RS256`), surface `cose.alg` with the numeric COSE code, prefer `rpIdHash.hex`, `clientDataHash.sha256`, and `signedBytes.sha256` keys, and render single-byte hex fields without a `0x` prefix.
  - `build.signatureBase` must also publish the byte lengths for `authenticatorData`, the derived `clientDataHash`, and the concatenated payload (`signedBytes.len.bytes`). Expose the concatenated buffer as `signedBytes.hex` (attribute type HEX) and emit a `signedBytes.preview` string that shows the first 16 and last 16 bytes (hex-encoded, separated by an ellipsis) so operators can sanity-check long payloads without scrolling while still retaining the full hex dump.
  - Authenticator data logging: expose a `flags.bits.*` map alongside the raw `flags.byte`, covering `UP`, `RFU1`, `UV`, `BE`, `BS`, `RFU2`, `AT`, and `ED`, and record the relying-party policy via `userVerificationRequired` plus a derived `uv.policy.ok = (!userVerificationRequired) || flags.bits.UV` attribute.
  - Canonicalise the relying party identifier before computing digests, expose the normalised value (`rpId.canonical`), surface the derived SHA-256 digest (`rpIdHash.expected`), and include a boolean comparison result (`rpIdHash.match`) so traces highlight mismatches without relying on the verifier exception alone.
  - COSE key decoding: surface the decoded key metadata for every algorithm by emitting `cose.kty`, `cose.kty.name`, and `cose.alg.name`, plus curve identifiers (`cose.crv`, `cose.crv.name`) and coordinate/modulus/exponent values encoded as base64url (e.g., `cose.x.b64u`, `cose.y.b64u`, `cose.n.b64u`, `cose.e.b64u`) while retaining the raw hex dump for parity.
  - Public key identification: derive the RFC 7638 JWK thumbprint for the credential public key and expose it as `publicKey.jwk.thumbprint.sha256` alongside the decoded COSE fields so traces provide a stable identifier for cross-facade comparisons.
  - Client data logging: record `expected.type`, `type.match`, `challenge.b64u`, `challenge.decoded.len`, `origin.expected`, `origin.match`, and `tokenBinding.*` attributes alongside the existing JSON/hash fields to document successful validation context. Mismatches remain enforced by the application/verifier layers; traces only emit results for accepted inputs.
- **Common presentation** – Every trace lists `operation`, `metadata`, and numbered `steps` with nested key/value lines. Each step should end with an optional `spec:` line referencing the governing section.

## Success Criteria
- All relevant flows expose deterministic, step-by-step traces when verbose mode is enabled and remain unchanged otherwise.
- Automated tests confirm presence/absence of traces across facades and validate key step contents.
- Documentation, knowledge map, and roadmap entries reflect the new verbose tracing capability.
- `./gradlew spotlessApply check` completes successfully after implementation.

## Rollout & Future Work
- Evaluate adding reusable visualisations (e.g., diff views, highlight toggles) once the initial terminal-style panel ships.
- Consider optional exporters (files, web sockets) under future features if persistent audit history becomes desirable.
- Implement opt-in tier controls via the dedicated Feature 036 once the shared helper lands.
