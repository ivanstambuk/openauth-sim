# Feature 001 Tasks – Core Credential Domain

_Status:_ Complete  
_Last updated:_ 2025-11-10_

## Checklist
- [x] T-001-01 – Close clarifications, update roadmap/knowledge map, and capture analysis-gate notes (FR-001-01–FR-001-08, S-001-01–S-001-05).
  _Intent:_ Ensure the spec/plan/tasks reflect the agreed OCRA-only scope before coding began.
  _Verification commands:_
  - `less docs/4-architecture/features/001/spec.md`
  - `rg -n "Feature 001" docs/4-architecture/roadmap.md`

- [x] T-001-02 – Add failing descriptor + validation tests covering required fields, suite parsing, and sanitized telemetry (FR-001-01, FR-001-06, S-001-01, S-001-03).
  _Intent:_ Define the immutable descriptor contract and error conditions ahead of implementation.
  _Verification commands:_
  - `./gradlew --no-daemon :core:test --tests "*OcraCredentialDescriptorTest"`

- [x] T-001-03 – Implement descriptors, validation helpers, and error diagnostics; rerun targeted tests (FR-001-01, FR-001-07, S-001-01, S-001-03).
  _Intent:_ Drive the red tests from T-001-02 green while preserving secret redaction.
  _Verification commands:_
  - `./gradlew --no-daemon :core:test --tests "*OcraCredentialDescriptorTest"`
  - `./gradlew --no-daemon spotlessApply`

- [x] T-001-04 – Build shared-secret normalization utilities plus property-based tests (FR-001-02, S-001-02).
  _Intent:_ Canonicalize RAW/HEX/Base64 inputs and prove round-trips + failure modes.
  _Verification commands:_
  - `./gradlew --no-daemon :core:test --tests "*SecretMaterialCodecTest"`

- [x] T-001-05 – Implement registry metadata + persistence envelopes with upgrade hooks (FR-001-03, FR-001-04, S-001-03, S-001-04).
  _Intent:_ Expose capability metadata and ensure stored descriptors upgrade seamlessly.
  _Verification commands:_
  - `./gradlew --no-daemon :core:test --tests "*CredentialRegistryTest"`
  - `./gradlew --no-daemon :core:test --tests "*CredentialEnvelopeUpgradeTest"`

- [x] T-001-06 – Deliver RFC 6287 compliance fixtures (including S064/S128/S256/S512 session vectors) and implement `OcraResponseCalculator` (FR-001-05, S-001-05).
  _Intent:_ Guarantee the execution helper reproduces published OTPs for all supported suites.
  _Verification commands:_
  - `./gradlew --no-daemon :core:test --tests "*OcraRfc6287ComplianceTest"`

- [x] T-001-07 – Wire session-aware helper into CLI maintenance flows and document provenance for the vectors (FR-001-05, S-001-05).
  _Intent:_ Prove downstream consumers can call the calculator while keeping telemetry sanitized.
  _Verification commands:_
  - `./gradlew --no-daemon :cli:test --tests "*MaintenanceCli*"`
  - `rg -n "S064" docs/4-architecture/features/001/plan.md`

## Verification Log
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check`

## Notes / TODOs
- Legacy Phase/T0xx tables live in git history prior to 2025-11-09 for auditors who need the original increment breakdown.
