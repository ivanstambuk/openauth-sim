# Feature 038 Tasks - Evaluation Result Preview Table

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-11 |
| Linked plan | `docs/4-architecture/features/038/plan.md` |

> Checklist aligns with the Increment Map. Entries capture intent, verification commands, and references to FR/NFR/S IDs.

## Checklist
- [x] T-038-01 - REST evaluation previews (FR-038-01, S-038-01).  
  _Intent:_ Add `window.backward/forward` DTOs, remove evaluation drift fields, emit ordered `previews` array, update integration tests, regenerate OpenAPI snapshot.  
  _Verification commands:_  
  - 2025-11-02 - `./gradlew --no-daemon :rest-api:test`  
  - 2025-11-02 - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`

- [x] T-038-02 - Application + CLI propagation (FR-038-02, NFR-038-01, S-038-02).  
  _Intent:_ Expose preview windows through application services, drop evaluation drift options, add CLI window flags and ordered preview outputs.  
  _Verification commands:_  
  - 2025-11-01 - `./gradlew --no-daemon :application:test`  
  - 2025-11-01 - `./gradlew --no-daemon :cli:test`  
  - 2025-11-01 - `./gradlew --no-daemon spotlessApply check`

- [x] T-038-03 - Operator UI integration (FR-038-03, NFR-038-02, S-038-03).  
  _Intent:_ Render preview tables in result cards (stored + inline), add Preview window offsets controls, ensure Replay drift inputs remain.  
  _Verification commands:_  
  - 2025-11-01 - `./gradlew --no-daemon :rest-api:test :ui:test`

- [x] T-038-04 - Accessibility, telemetry, docs (FR-038-04, NFR-038-03, S-038-04).  
  _Intent:_ Complete accessibility review, document preview behaviour, update roadmap/knowledge map/how-tos, capture telemetry evidence.  
  _Verification commands:_  
  - 2025-11-01 - `./gradlew --no-daemon :rest-api:test`  
  - 2025-11-01 - `./gradlew --no-daemon spotlessApply check`

- [x] T-038-05 - Helper-text cleanup (FR-038-03, S-038-05).  
  _Intent:_ Remove redundant helper sentence below Preview window offsets controls, drop `aria-describedby` bindings, rerun aggregate Gradle gate.  
  _Verification commands:_  
  - 2025-11-08 - `./gradlew --no-daemon spotlessApply check`

## Verification Log
- 2025-11-02 - `./gradlew --no-daemon :rest-api:test`
- 2025-11-02 - `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`
- 2025-11-01 - `./gradlew --no-daemon :application:test`
- 2025-11-01 - `./gradlew --no-daemon :cli:test`
- 2025-11-01 - `./gradlew --no-daemon spotlessApply check`
- 2025-11-01 - `./gradlew --no-daemon :rest-api:test :ui:test`
- 2025-11-01 - `./gradlew --no-daemon :rest-api:test`
- 2025-11-08 - `./gradlew --no-daemon spotlessApply check`

## Notes / TODOs
- Replay result cards remain a follow-up feature; preview tables currently scope to evaluation flows only.
