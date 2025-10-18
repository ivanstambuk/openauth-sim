# Feature Plan 027 – Unified Credential Store Naming

_Linked specification:_ `docs/4-architecture/specs/feature-027-unified-credential-store.md`  
_Status:_ In Progress  
_Last updated:_ 2025-10-18

## Objective
Deliver a seamless transition to the shared `credentials.db` default across all simulator facades while maintaining compatibility with existing protocol-specific database files.

## Current Context
- Prior to this work, REST and the OCRA CLI defaulted to `ocra-credentials.db` while other CLIs used protocol-specific filenames, forcing operators to reconcile paths manually.
- Documentation and knowledge artefacts assume the OCRA-specific filename for cross-facade sharing.
- No automated migration exists for users with pre-populated OCRA/TOTP/HOTP/FIDO2 stores.

## Increment Breakdown (≤10 minutes each)
1. **I1 – Documentation & governance sync**
   - Update the roadmap, knowledge map, and current-session snapshot to reference Feature 027.
   - Capture the resolved clarification (new default name) in the specification and open-questions log.
   - Ensure the feature tasks checklist mirrors this plan.
   - _2025-10-18 – Completed: spec/plan/tasks created, roadmap/knowledge map/current-session updated, clarification logged._

2. **I2 – Persistence factory fallback**
   - Update `CredentialStoreFactory.resolveDatabasePath` to prefer `credentials.db` and probe legacy filenames when no explicit path is provided.
   - Emit informational logging/telemetry when a legacy file is selected.
   - Refresh `CredentialStoreFactoryTest` coverage and add focused unit tests for fallback ordering.
   - _2025-10-18 – Completed: factory prioritises `credentials.db`, logs legacy fallbacks, and regression tests cover unified plus legacy discovery paths._

3. **I3 – Facade defaults & tests**
   - Replace default filename constants in CLI modules and REST configuration with the unified name.
   - Update CLI help text, REST tests, Selenium scenarios, and docs referencing the old filenames.
   - Run targeted module tests plus `./gradlew spotlessApply check`, then capture rollout guidance in docs/2-how-to.
   - _2025-10-18 – Completed: CLI/REST defaults updated, targeted CLI/REST/Selenium suites rerun; full `spotlessApply check` scheduled post-doc refresh._

4. **I4 – Migration guidance**
   - Document fallback behaviour and future deprecation notes in `docs/2-how-to/configure-persistence-profiles.md` and related guides.
   - Note the change in release/roadmap documentation and update telemetry guidance if logging changed.
   - _2025-10-18 – Completed: how-to guides, roadmap, and spec add unified default messaging plus legacy fallback guidance._

## Dependencies
- `infra-persistence` module for the shared factory logic.
- CLI and REST modules consuming the factory.
- Documentation stack under `docs/`.

## Risks & Mitigations
- **Risk:** Operators might unintentionally create a new empty `credentials.db` while legacy files still exist.  
  **Mitigation:** Probe legacy filenames and log clearly when fallbacks occur.
- **Risk:** Hard-coded tests assume legacy filenames.  
  **Mitigation:** Inventory and update tests across modules in Increment I3.

## Validation
- Execute `./gradlew --no-daemon :infra-persistence:test :cli:test :rest-api:test spotlessApply check`.
- Manual smoke test: launch CLI/REST without explicit database path to confirm the new default file is created and reused after restart when legacy files are absent; repeat with only a legacy file present to ensure fallback.
