# Feature 022 – Task Checklist

_Status: Draft_
_Last updated: 2025-10-05_

## Tasks (≤10 min each)
- ☑ T2201 – Add failing HOTP generator/validator unit tests covering counter rollover, digit length variants, and secret bounds.
- ☑ T2202 – Implement HOTP domain logic to satisfy T2201 and extend mutation/ArchUnit coverage.
- ☑ T2203 – Add failing MapDB integration tests mixing OCRA + HOTP credentials via `CredentialStoreFactory`.
- ☑ T2204 – Implement shared persistence updates for HOTP records and make T2203 pass.
- ☑ T2205 – Add failing application-layer tests for HOTP evaluation/issuance telemetry adapters.
- ☑ T2206 – Implement application services and telemetry wiring to satisfy T2205.
- ☑ T2207 – Add failing CLI command tests (import/list/evaluate) asserting telemetry frames.
- ☑ T2208 – Implement CLI HOTP commands and ensure T2207 passes.
- ☑ T2209 – Add failing REST MockMvc tests + OpenAPI snapshot expectations for HOTP evaluation endpoints.
- ☑ T2210 – Implement REST HOTP endpoints, update OpenAPI artifacts, and satisfy T2209.
- ☑ T2211 – Update how-to docs, roadmap highlights, knowledge map, and rerun `./gradlew spotlessApply check` (2025-10-05 run reached Jacoco branch coverage 0.9002 / line 0.9706 after HOTP REST coverage additions).
- ☑ T2212 – Add failing operator console integration/system tests for HOTP stored credential evaluation flows.
- ☑ T2213 – Implement HOTP stored credential evaluation in the operator console and ensure T2212 passes.
- ☑ T2214 – Add failing operator console integration/system tests for HOTP inline evaluation flows plus accessibility assertions.
- ☑ T2215 – Implement HOTP inline evaluation in the operator console and ensure T2214 passes.
- ☑ T2216 – Update operator how-to docs, roadmap notes, knowledge map, and rerun `./gradlew spotlessApply check` after HOTP UI work.
- ☑ T2217 – Add failing REST MockMvc tests + OpenAPI expectations for `POST /api/v1/hotp/replay` (stored + inline) asserting counters remain unchanged and telemetry uses `hotp.replay` (2025-10-05 run: `./gradlew :rest-api:test --tests "io.openauth.sim.rest.HotpReplayEndpointTest"` initially failed pending implementation; see T2218 for the passing follow-up).
- ☑ T2218 – Implement HOTP replay service/controller wiring, ensure counters are read-only, refresh OpenAPI snapshots, and satisfy T2217 (`./gradlew :application:test --tests "io.openauth.sim.application.hotp.HotpReplayApplicationServiceTest"` + `OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, followed by full `./gradlew :rest-api:test`).
- ☑ T2219 – Add failing Selenium coverage for HOTP replay UI (stored + inline) mirroring OCRA behaviour (sample data, advanced toggles, telemetry identifier surfacing).
- ☑ T2220 – Implement HOTP replay UI template/JS wiring and make T2219 pass.
- ☑ T2221 – Update operator docs, roadmap, knowledge map, and telemetry references for HOTP replay; rerun `./gradlew spotlessApply check`.
- ☑ T2222 – Add failing operator console integration/system test asserting HOTP evaluate/replay tab selection writes `protocol=hotp` and `tab=<active>` query params for deep-link restoration. (`./gradlew :rest-api:test --tests "…hotpTabsPersistQueryParameters"` initially red.)
- ☑ T2223 – Implement HOTP operator console query-param updates and ensure T2222 passes. (`./gradlew spotlessApply check` green 2025-10-05; Selenium `hotpReplayDeepLinkSurvivesRefresh` confirms refresh persistence.)
- ☑ T2224 – Update operator console Selenium tests to fail when inline/stored evaluation panels still render headings or hint copy after mode selection (`./gradlew :rest-api:test --tests "io.openauth.sim.rest.ui.OperatorConsoleUnificationSeleniumTest"` red as expected).
- ☑ T2225 – Remove the HOTP evaluation headings/hints from the UI template/JS, adjust accessibility attributes, and make T2224 pass with targeted UI suites plus `./gradlew spotlessApply check` (green 2025-10-05).
- ☑ T2226 – Add failing REST + UI tests ensuring inline HOTP requests omit the identifier field and update OpenAPI expectations accordingly. (2025-10-05: `HotpEvaluationServiceTest`, controller tests, and Selenium coverage updated to expect no identifier.)
- ☑ T2227 – Strip identifier handling from HOTP inline evaluation across REST/application/core layers, update UI payloads, regenerate snapshots, and rerun `./gradlew spotlessApply check`. (2025-10-05: identifier removed; OpenAPI regenerated; build passes save for known spotless tool issue documented in build notes.)
- ☑ T2228 – Force google-java-format 1.28.0 across Spotless and annotation processor configurations, refresh dependency locks, and rerun `./gradlew spotlessApply check` to confirm the formatter mismatch is resolved. (2025-10-05: added resolution strategy force, rewrote locks via targeted compile/compileTest tasks, and verified Spotless/build.)

Mark tasks as work completes and record tooling outcomes within the feature plan.
