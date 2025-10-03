# Feature 017 – Task Checklist

_Status: Draft_
_Last updated: 2025-10-03_

## Tasks (≤10 min each)
- [x] T1701 – Add Selenium coverage expecting unified operator console route with protocol tabs (fails until implementation).
- [x] T1702 – Update existing Selenium suites to exercise `/ui/console` while preserving regression coverage.
- [x] T1703 – Implement unified Thymeleaf template/fragment structure and routing updates; re-run `./gradlew :rest-api:test` (OCRA tab only).
- [x] T1704 – Introduce dark theme token set, responsive layout changes, and adjust accessibility checks.
- [x] T1705 – Rewire evaluation/replay flows within the unified console, ensuring REST/telemetry tests pass.
- [x] T1706 – Update documentation, knowledge map, and rerun `./gradlew spotlessApply check` for final verification.
- [x] T1707 – Remove or redirect legacy `/ui/ocra/evaluate` and `/ui/ocra/replay` routes; update tests to enforce `/ui/console` as sole entry point.
- [x] T1708 – Reposition replay tab sample loader beneath the mode selector, add Selenium assertion, and rerun `./gradlew :rest-api:test spotlessApply check`.
- [x] T1709 – Drop replay result telemetry detail fields, update scripts/tests accordingly, and rerun `./gradlew :rest-api:test spotlessApply check`.
- [x] T1710 – Stack evaluation/replay metadata as single-row entries, adjust CSS/tests, and rerun `./gradlew :rest-api:test spotlessApply check`.
- [x] T1711 – Remove Suite from evaluation result metadata, update scripts/tests, and rerun `./gradlew :rest-api:test spotlessApply check`.

Update this checklist as increments progress; keep tests ahead of implementation per VDD cadence.
