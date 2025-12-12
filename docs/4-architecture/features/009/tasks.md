# Feature 009 Tasks – Operator Console Infrastructure

_Status:_ In progress
_Last updated:_ 2025-12-11

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).

## Checklist
- [ ] T-009-01 – Enforce operator-console facade seams via application layer + CredentialStoreFactory (NFR-facade-seam).  
  _Intent:_ Ensure operator console (REST/UI/templates) avoids direct `io.openauth.sim.core..`/`MapDbCredentialStore`, relying on `application` services and `CredentialStoreFactory`; add ArchUnit coverage and refactor UI/REST wiring as needed.  
  _Verification:_  
  - `./gradlew --no-daemon :core-architecture-tests:test`  
  - `./gradlew --no-daemon :rest-api:test :ui:test`  
  - `./gradlew --no-daemon spotlessApply check`

## Verification Log
- 2025-11-13 – `./gradlew --no-daemon spotlessApply check` (Implementation Drift Gate verification run)

## Notes / TODOs
- Keep the knowledge map/roadmap aligned with Feature 009 ownership; update references when upstream features change scope.
