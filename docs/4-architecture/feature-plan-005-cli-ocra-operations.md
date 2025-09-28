# Feature Plan 005 – CLI OCRA Operations

_Status: Draft_
_Last updated: 2025-09-28_

## Objective
Bring the CLI facade to parity with the REST endpoint for OCRA by adding commands to import, list, delete, and evaluate credentials. Evaluation must support both credential-id lookup and inline secret parameters, reusing persistence and validation rules established in Features 003/004.

Reference specification: `docs/4-architecture/specs/feature-005-cli-ocra-operations.md`.

## Success Criteria
- Operators can persist OCRA credentials via CLI commands and confirm they exist.
- CLI evaluation reuses stored descriptors or inline secrets, producing OTPs identical to core/REST outputs.
- Validation errors mirror REST reason codes (`credential_conflict`, `credential_missing`, etc.) and remain sanitized.
- Documentation (`docs/2-how-to`, command help) explains the new commands.

## Proposed Increments
- R017 – Draft/align spec, plan, and tasks; identify open questions if any. ✅ 2025-09-28
- R018 – ✅ 2025-09-28 Add failing CLI tests (Picocli harness) covering import/list/delete/evaluate flows, including credential-id vs inline mode; telemetry assertions now codified in new `OcraCliCommandTest`.
- R019 – ✅ 2025-09-28 Implemented Picocli CLI handlers for import/list/delete/evaluate, wired to `MapDbCredentialStore` + OCRA adapters, and emitted sanitized telemetry lines (`./gradlew :cli:test`).
- R020 – ✅ 2025-09-28 Updated CLI how-to, captured telemetry snapshot for `cli.ocra` events, reran `./gradlew :cli:test` and `./gradlew spotlessApply check`.

## Dependencies
- Picocli CLI framework already in the project (`MaintenanceCli`): extend or add new command classes.
- Requires access to the same `CredentialStore` and OCRA persistence adapters used by core/REST.

## Analysis Gate Notes
- TBD after tests are authored; ensure CLI telemetry redaction is enforced.

Update this plan as increments progress. Remove once Feature 005 ships.
