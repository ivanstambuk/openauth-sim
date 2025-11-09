# Feature Plan 027 – Unified Credential Store Naming

_Linked specification:_ `docs/4-architecture/features/027/spec.md`  
_Status:_ Complete  
_Last updated:_ 2025-10-29

## Objective
Deliver a seamless transition to the shared `credentials.db` default across all simulator facades while retiring compatibility shims for legacy protocol-specific database files.

## Current Context
- Prior to this work, REST and the OCRA CLI defaulted to `ocra-credentials.db` while other CLIs used protocol-specific filenames, forcing operators to reconcile paths manually.
- Documentation and knowledge artefacts assume the OCRA-specific filename for cross-facade sharing.
- Operators with pre-populated OCRA/TOTP/HOTP/FIDO2 stores must now rename or explicitly configure their database paths; no automated migration is provided.

## Increment Breakdown (≤30 minutes each)
1. **I1 – Documentation & governance sync**
   - Update the roadmap, knowledge map, and current-session snapshot to reference Feature 027.
   - Capture the resolved clarification (new default name) in the specification and open-questions log.
   - Ensure the feature tasks checklist mirrors this plan.
   - _2025-10-18 – Completed: spec/plan/tasks created, roadmap/knowledge map/current-session updated, clarification logged._

2. **I2 – Persistence factory update**
   - Update `CredentialStoreFactory.resolveDatabasePath` to always return `credentials.db` when no explicit path is provided.
   - Remove legacy filename probing and related telemetry/logging.
   - Refresh `CredentialStoreFactoryTest` coverage to assert the simplified behaviour.
   - _2025-10-19 – Completed: factory now returns `credentials.db` by default with no legacy probes; tests updated accordingly._

3. **I3 – Facade defaults & tests**
   - Replace default filename constants in CLI modules and REST configuration with the unified name.
   - Update CLI help text, REST tests, Selenium scenarios, and docs referencing the old filenames.
   - Run targeted module tests plus `./gradlew spotlessApply check`, then capture rollout guidance in docs/2-how-to.
   - _2025-10-18 – Completed: CLI/REST defaults updated, targeted CLI/REST/Selenium suites rerun; full `spotlessApply check` scheduled post-doc refresh._

4. **I4 – Migration guidance**
   - Document manual migration guidance (rename legacy files or set explicit paths) in `docs/2-how-to/configure-persistence-profiles.md` and related guides.
   - Note the change in release/roadmap documentation and update telemetry guidance if logging changed.
   - _2025-10-19 – Completed: how-to guides, roadmap, and spec now instruct operators to migrate legacy files manually; fallback language removed._

## Dependencies
- `infra-persistence` module for the shared factory logic.
- CLI and REST modules consuming the factory.
- Documentation stack under `docs/`.

## Risks & Mitigations
- **Risk:** Operators might forget to migrate legacy `*-credentials.db` files and assume they are still loaded automatically.  
  **Mitigation:** Highlight the manual migration requirement across docs and CLI/REST startup logs when the unified default is created.
- **Risk:** Hard-coded tests assume legacy filenames.  
  **Mitigation:** Inventory and update tests across modules in Increment I3.

## Validation
- Execute `./gradlew --no-daemon :infra-persistence:test :cli:test :rest-api:test spotlessApply check`.
- Manual smoke test: launch CLI/REST without explicit database path to confirm the new default file is created and reused after restart; repeat with a legacy file by explicitly pointing the configuration at it to verify manual migration guidance.

## Completion Notes
- 2025-10-19 – I1–I4 delivered factory updates, CLI/REST defaults, regression coverage, and documentation guidance; validation stack executed (`./gradlew --no-daemon :infra-persistence:test :cli:test :rest-api:test spotlessApply check`).
- 2025-10-29 – Documentation artefacts (roadmap, knowledge map, how-to guides) verified in sync; plan closed with no outstanding increments.
