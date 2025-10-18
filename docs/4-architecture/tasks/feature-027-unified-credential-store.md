# Feature 027 Tasks – Unified Credential Store Naming

_Linked plan:_ `docs/4-architecture/feature-plan-027-unified-credential-store.md`  
_Status:_ In Progress  
_Last updated:_ 2025-10-18

☑ **T2701 – Governance sync**
  ☑ Update roadmap with Feature 027 entry and reference the new spec/plan/tasks.  
  ☑ Append knowledge map and current-session notes to reflect the shared filename direction.

☑ **T2702 – Persistence fallback implementation**
  ☑ Modify `CredentialStoreFactory.resolveDatabasePath` to prioritise `credentials.db` and detect legacy files.  
  ☑ Add telemetry/logging for legacy fallback selection.  
  ☑ Extend `CredentialStoreFactoryTest` to cover new behaviour.

☑ **T2703 – Facade defaults & regression tests**
  ☑ Replace CLI/REST default filename constants and help text.  
  ☑ Update REST/CLI tests, Selenium scenarios, and OpenAPI snapshots impacted by the filename change.  
  ☑ Run targeted Gradle tasks plus `spotlessApply check`.

☑ **T2704 – Documentation refresh**
  ☑ Update how-to guides, configuration references, and release notes with the unified filename and fallback explanation.  
  ☑ Document manual migration guidance and future deprecation steps.
