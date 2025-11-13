# Feature 012 Tasks – Core Cryptography & Persistence

_Status:_ Complete  
_Last updated:_ 2025-11-13

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).

## Checklist
- _No active tasks._ Capture the next persistence increment (≤90 min plan effort) with explicit FR/NFR/Scenario references before editing docs or helpers.

## Verification Log
- 2025-11-13 – `./gradlew --no-daemon :cli:test --tests "*MaintenanceCli*"` (maintenance CLI verification; BUILD SUCCESSFUL, config cache stored)
- 2025-11-13 – `./gradlew --no-daemon :core:test --tests "*MapDbCredentialStoreTest*"` (persistence encryption coverage; BUILD SUCCESSFUL, config cache stored)
- 2025-11-13 – `./gradlew --no-daemon spotlessApply check` (persistence drift gate verification, 7 s, 96 tasks: 2 executed, 94 up-to-date)
- 2025-11-11 – `rg "credentials.db" docs`
- 2025-11-11 – `./gradlew --no-daemon spotlessApply check`

## Notes / TODOs
- Track persistence benchmark reruns + encryption key-management automation as future backlog items.
- Consider scripting a lint that ensures only `credentials.db` appears as the default path across docs/code.
- Follow-up: Promote the MapDb encryption tests to `infra-persistence` when they land so the verification command can shift from `:core:test --tests "*MapDbCredentialStoreTest*"` back to the persistence module.
- Legacy Coverage – T-012-L1 (Feature 002 deployment profiles + telemetry contracts). _Intent:_ Verify FR-012-01..04 and NFR-012-01/02 describe the IN_MEMORY/FILE/CONTAINER profiles, override hooks, and telemetry previously tracked in Feature 002. _Verification commands:_ `./gradlew --no-daemon :infra-persistence:test`, `rg "persistence.cache.profile" -n docs`, `./gradlew --no-daemon spotlessApply check`. _Notes:_ `_current-session.md` contains the 2025-11-11 command log for this verification.
- Legacy Coverage – T-012-L2 (Feature 027 maintenance CLI + benchmarks). _Intent:_ Ensure FR-012-05/06 and NFR-012-03 capture maintenance CLI usage, benchmarks, and documentation hooks. _Verification commands:_ `./gradlew --no-daemon :cli:test --tests "*MaintenanceCli*"`, `rg "credentials.db" docs`. _Notes:_ Scenario references updated under S-012-02; see `_current-session.md`.
- Legacy Coverage – T-012-L3 (Feature 028 IDE remediation + encryption helpers). _Intent:_ Confirm FR-012-07 and NFR-012-04 cover IDE remediation, optional encryption, and governance logging drawn from Feature 028. _Verification commands:_ `./gradlew --no-daemon spotlessApply check`, `./gradlew --no-daemon :core:test --tests "*MapDbCredentialStoreTest*"`. _Notes:_ Backlog follow-ups noted in plan I4; verification logged 2025-11-13.
