# Feature 005 Tasks – CLI OCRA Operations

_Status:_ Complete  
_Last updated:_ 2025-11-10_

## Checklist
- [x] T0501 – Finalise spec/plan alignment and record roadmap + knowledge map entries (S05-01, S05-02, S05-03, S05-04, S05-05).
  _Intent:_ Capture scope/clarifications and ensure operators know where the CLI experience lands.
  _Verification commands:_
  - `less docs/4-architecture/features/005/spec.md`
  - `rg -n "Feature 005" docs/4-architecture/roadmap.md`
  _Notes:_ Documented dual-mode CLI scope and logged roadmap/knowledge map references before coding.

- [x] T0502 – Add failing Picocli tests for import/list/delete/evaluate flows (stored vs inline) before implementation (CLI-OCRA-001–CLI-OCRA-004, S05-01–S05-04).
  _Intent:_ Lock regression coverage for persistence, mutual exclusivity, validation, and telemetry errors.
  _Verification commands:_
  - `./gradlew --no-daemon :cli:test --tests "*OcraCli*"`
  _Notes:_ Tests assert OTP parity with REST/core and capture telemetry reason-code placeholders.

- [x] T0503 – Implement CLI handlers, persistence wiring, and telemetry sanitisation for all subcommands (CLI-OCRA-001–CLI-OCRA-005, S05-01–S05-05).
  _Intent:_ Drive red tests green while keeping command ergonomics/exit codes deterministic.
  _Verification commands:_
  - `./gradlew --no-daemon :cli:test`
  - `rg -n "event=cli.ocra" cli/src`
  _Notes:_ Added persistence integration, mutual exclusivity checks, and hashed credential IDs in telemetry output.

- [x] T0504 – Refresh operator docs/how-to sections, document telemetry expectations, and run the full gate (S05-02, S05-04, S05-05).
  _Intent:_ Ensure documentation matches the shipped CLI behaviour and that formatting/lint checks remain green.
  _Verification commands:_
  - `./gradlew --no-daemon spotlessApply check`
  - `rg -n "CLI OCRA" docs/2-how-to`
  _Notes:_ Updated how-to guide + knowledge map and archived telemetry samples for CLI commands.

## Verification Log
- 2025-11-10 – `./gradlew --no-daemon spotlessApply check`

## Notes / TODOs
- Legacy R01x task table (import/list/delete/evaluate increments) lives in git history prior to 2025-11-09 for forensic reference.
