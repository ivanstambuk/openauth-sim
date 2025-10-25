# Feature 036 Tasks – Verbose Trace Tier Controls

_Linked plan:_ `docs/4-architecture/feature-plan-036-verbose-trace-tier-controls.md`  
_Status:_ Draft  
_Last updated:_ 2025-10-25

☐ **T3601 – Tier helper specification tests (red)**  
 ☐ Add failing unit tests under `core` describing tier definitions (`normal`, `educational`, `lab-secrets`) and attribute tagging expectations.  
 ☐ Command: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.trace.TraceAttributeTierTest"`

☐ **T3602 – Tier helper implementation (green)**  
 ☐ Implement tier-aware filtering utilities and integrate minimum-tier metadata into the trace model.  
 ☐ Command: `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.trace.*Tier*"`

☐ **T3603 – Protocol tagging rollout**  
 ☐ Stage failing application tests for HOTP/TOTP/OCRA/FIDO2 verbose traces covering tier behaviour.  
 ☐ Tag attributes with minimum tiers and rerun module suites plus CLI/REST coverage.  
 ☐ Commands: `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.*VerboseTraceTierTest"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.*VerboseTraceTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.*VerboseTraceTest"`

☐ **T3604 – Facade contract updates**  
 ☐ Refresh CLI snapshots, REST DTO/OpenAPI fixtures, and operator UI fixtures to assert tier-specific payloads.  
 ☐ Commands: `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`; `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.*VerboseTraceTest"`; `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.*VerboseTraceTest"`; run UI Selenium target as needed.

☐ **T3605 – Documentation & knowledge sync**  
 ☐ Update specs, plans, knowledge map, roadmap notes, and how-to guides to reflect tier filtering; record changes in `docs/_current-session.md`.  
 ☐ Command: `./gradlew --no-daemon spotlessApply check`
