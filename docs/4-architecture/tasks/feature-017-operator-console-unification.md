# Feature 017 – Task Checklist

_Status: Draft_
_Last updated: 2025-10-04_

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
- [x] T1712 – Add failing Selenium/Web layer coverage for the seeding control + REST endpoint contract; run `./gradlew :rest-api:test` to confirm red.
- [x] T1713 – Implement the seeding endpoint, domain wiring, and UI control (append-only) with telemetry updates; rerun `./gradlew :rest-api:test`.
- [x] T1714 – Update docs/knowledge map for the seeding workflow and rerun `./gradlew spotlessApply check`.
- [x] T1715 – Add Selenium/Web tests covering query-parameter deep links and history behaviour for `/ui/console`, ensure they fail prior to implementation.
- [x] T1716 – Implement query-parameter state management + history handling for the console, run `./gradlew :rest-api:test` to verify green.
- [x] T1717 – Refresh docs/knowledge map and telemetry notes for the stateful URL behaviour, rerun `./gradlew spotlessApply check`.
- [x] T1718 – Add failing Selenium coverage asserting the evaluation result no longer renders a Sanitized row. (_2025-10-04_)
- [x] T1719 – Remove the Sanitized row from evaluation results, update Selenium expectations, and rerun `./gradlew :rest-api:test spotlessApply check`. (_2025-10-04_)
- [x] T1720 – Update Selenium coverage for seeding status placement beneath the seed control with persistent hint text. (_2025-10-04_)
- [x] T1721 – Implement seeding status layout/script adjustments and rerun `./gradlew :rest-api:test spotlessApply check`. (_2025-10-04_)
- [x] T1722 – Add Selenium coverage asserting zero-added seeding responses apply the warning styling. (_2025-10-04_)
- [x] T1723 – Implement warning styling for zero-added seeding results, rerun `./gradlew :rest-api:test spotlessApply check`. (_2025-10-04_)
- [x] T1724 – Relocate the shared console stylesheet to `/ui/console/console.css`, update templates/JS/tests, and rerun `./gradlew spotlessApply check`. (_2025-10-15_)

Update this checklist as increments progress; keep tests ahead of implementation per VDD cadence.
