# Feature 035 – Evaluate & Replay Audit Tracing

_Status: Proposed_  
_Last updated: 2025-10-22_

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

## Requirements
- Define a structured trace model under `core/` that can capture ordered steps, labelled intermediate values, and protocol-specific annotations while remaining extensible for future credential types.
- Update HOTP, TOTP, OCRA, and FIDO2 evaluation/replay/attestation services to populate the trace model when verbose mode is requested, documenting each cryptographic operation (e.g., key derivation, HMAC rounds, signature verification) with raw inputs/outputs.
- Propagate opt-in flags from each facade to the application layer:
  - CLI: add a `--verbose` (or comparable) option per relevant command, emitting the trace to stdout after the primary result while preserving current exit codes.
  - REST: accept a boolean (or enum) verbose field within request DTOs; include a `trace` payload in the JSON response when requested.
  - Operator UI: provide a per-request control (e.g., toggle or checkbox) that requests verbose mode from REST, and render the returned trace in an interactive panel that mimics a terminal log (collapsed by default).
- Deliver at least two concrete UI layout proposals for the trace panel (e.g., bottom dock, side drawer) as part of the feature plan and record the accepted option under `## Clarifications` before implementation.
- Maintain ordering between the algorithm’s execution steps and the trace output so users can follow the computation sequentially.
- Ensure traces are never written to disk, logged through telemetry, or otherwise persisted outside their immediate response plumbing.
- Add automated coverage (unit/integration tests per facade) that verifies traces materialise only when verbose mode is enabled and that representative steps appear for each protocol.
- Update operator/user documentation describing how to enable verbose tracing across CLI, REST, and UI channels.
- Define canonical operation identifiers per protocol (e.g., `hotp.evaluate.stored`, `totp.evaluate.inline`, `ocra.evaluate.inline`, `fido2.assertion.evaluate.stored`, `fido2.attestation.verify`) and reuse step identifiers such as `resolve.credential`, `generate.otp`, `verify.attestation`, and `assemble.result` so traces stay comparable across facades.

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

## Success Criteria
- All relevant flows expose deterministic, step-by-step traces when verbose mode is enabled and remain unchanged otherwise.
- Automated tests confirm presence/absence of traces across facades and validate key step contents.
- Documentation, knowledge map, and roadmap entries reflect the new verbose tracing capability.
- `./gradlew spotlessApply check` completes successfully after implementation.

## Rollout & Future Work
- Evaluate adding reusable visualisations (e.g., diff views, highlight toggles) once the initial terminal-style panel ships.
- Consider optional exporters (files, web sockets) under future features if persistent audit history becomes desirable.
