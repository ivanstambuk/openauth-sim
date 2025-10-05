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

Mark tasks as work completes and record tooling outcomes within the feature plan.
