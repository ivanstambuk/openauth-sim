# Feature 014 Tasks – Native Java API Facade

_Status:_ Complete  
_Last updated:_ 2025-11-15

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).

## Checklist
- [x] T-014-01 – Document Native Java API pattern in spec/plan (FR-014-01/02, S-014-01).  
  _Intent:_ Capture expectations for per-protocol Native Java entry points, DTOs, error semantics, telemetry reuse, and docs alignment.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`  
  _Notes:_ 2025-11-15 – Feature 014 spec/plan now define the Native Java API pattern (entry-point placement, naming, error handling, telemetry, Javadoc/docs integration) and reference OCRA’s existing API as the canonical precedent; per-protocol specs/plans for HOTP, TOTP, FIDO2/WebAuthn, EMV/CAP, and EUDIW link back to this pattern and treat their application-level services as façade seams.\  
\  
- [x] T-014-02 – Seed backlog entries for protocol features 001, 002, 004, 005, 006 (FR-014-03, S-014-02).  
  _Intent:_ Update those features’ plans’ Follow-ups/Backlog sections to reference Native Java API increments tied to Feature 014 and ADR-0007 (OCRA remains the reference implementation).  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`  
  _Notes:_ 2025-11-15 – Features 001, 002, 004, and 005 plans/tasks now include Native Java alignment notes and tasks (HOTP/TOTP/FIDO2/EMV/CAP seams + `*-from-java` guides), while Feature 006 tracks EUDIW Native Java API alignment as completed (T-006-28/29) with usage tests and `use-eudiw-from-java.md`; Feature 003’s spec/plan have been annotated to mark OCRA’s existing Native Java API as the reference pattern for Feature 014/ADR-0007.\  
\  
- [x] T-014-03 – Plan Javadoc and `*-from-java` alignment (FR-014-02/04).  
  _Intent:_ Decide how Javadoc for Native Java entry points will be generated/published and how `docs/2-how-to/*-from-java.md` guides will stay aligned with those APIs.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`  
  _Notes:_ 2025-11-15 – Updated the Feature 014 spec and plan to standardise on `:core:javadoc` and `:application:javadoc` for documentation, define a future aggregation task (for example, `:application:nativeJavaApiJavadoc`) owned by Feature 010 that exports a small Native Java API reference into `docs/3-reference/native-java-api/`, and clarified that `*-from-java` guides act as runbooks linking back to that reference rather than duplicating full API docs.

## Verification Log
- 2025-11-15 – `./gradlew --no-daemon :application:nativeJavaApiJavadoc` and `./gradlew --no-daemon spotlessApply check` (Feature 014 Native Java + Javadoc drift gate: verified seams, guides, usage tests, and Javadoc CI wiring as captured in the plan’s Implementation Drift Gate section).

## Notes / TODOs
- When individual protocol features pick up their Native Java API backlog items, ensure they reference FR-014-01..04 and scenarios S-014-01..02 from this feature, plus ADR-0007, so traceability remains clear.
