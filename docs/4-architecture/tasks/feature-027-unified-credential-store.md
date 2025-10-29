# Feature 027 Tasks – Unified Credential Store Naming

_Linked plan:_ `docs/4-architecture/feature-plan-027-unified-credential-store.md`  
_Status:_ Complete  
_Last updated:_ 2025-10-29

☑ **T2701 – Governance sync**
  ☑ Update roadmap with Feature 027 entry and reference the new spec/plan/tasks.  
  ☑ Append knowledge map and current-session notes to reflect the shared filename direction.

☑ **T2702 – Persistence resolver simplification**
  ☑ Modify `CredentialStoreFactory.resolveDatabasePath` to always return `credentials.db` when no explicit path is provided.  
  ☑ Remove telemetry/logging related to legacy fallback selection.  
  ☑ Refresh `CredentialStoreFactoryTest` coverage for the simplified behaviour.

☑ **T2703 – Facade defaults & regression tests**
  ☑ Replace CLI/REST default filename constants and help text.  
  ☑ Update REST/CLI tests, Selenium scenarios, and OpenAPI snapshots impacted by the filename change.  
  ☑ Run targeted Gradle tasks plus `spotlessApply check`.

☑ **T2704 – Documentation refresh**
  ☑ Update how-to guides, configuration references, and release notes with the unified filename and explicit migration guidance.  
  ☑ Document manual migration guidance and future deprecation steps.
