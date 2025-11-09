# Feature 017 – Task Checklist

_Status:_ Complete  
_Last updated:_ 2025-10-31

## Tasks (≤30 min each)
- [x] T1701 – Add Selenium coverage expecting unified operator console route with protocol tabs (fails until implementation). (S17-01)
- [x] T1702 – Update existing Selenium suites to exercise `/ui/console` while preserving regression coverage. (S17-01)
- [x] T1703 – Implement unified Thymeleaf template/fragment structure and routing updates; re-run `./gradlew :rest-api:test` (OCRA tab only). (S17-02)
- [x] T1704 – Introduce dark theme token set, responsive layout changes, and adjust accessibility checks. (S17-02)
- [x] T1705 – Rewire evaluation/replay flows within the unified console, ensuring REST/telemetry tests pass. (S17-02)
- [x] T1706 – Update documentation, knowledge map, and rerun `./gradlew spotlessApply check` for final verification. (S17-05)
- [x] T1707 – Remove or redirect legacy `/ui/ocra/evaluate` and `/ui/ocra/replay` routes; update tests to enforce `/ui/console` as sole entry point. (S17-04)
- [x] T1708 – Reposition replay tab sample loader beneath the mode selector, add Selenium assertion, and rerun `./gradlew :rest-api:test spotlessApply check`. (S17-02)
- [x] T1709 – Drop replay result telemetry detail fields, update scripts/tests accordingly, and rerun `./gradlew :rest-api:test spotlessApply check`. (S17-02)
- [x] T1710 – Stack evaluation/replay metadata as single-row entries, adjust CSS/tests, and rerun `./gradlew :rest-api:test spotlessApply check`. (S17-02)
- [x] T1711 – Remove Suite from evaluation result metadata, update scripts/tests, and rerun `./gradlew :rest-api:test spotlessApply check`. (S17-02)
- [x] T1712 – Add failing Selenium/Web layer coverage for the seeding control + REST endpoint contract; run `./gradlew :rest-api:test` to confirm red. (S17-03)
- [x] T1713 – Implement the seeding endpoint, domain wiring, and UI control (append-only) with telemetry updates; rerun `./gradlew :rest-api:test`. (S17-03)
- [x] T1714 – Update docs/knowledge map for the seeding workflow and rerun `./gradlew spotlessApply check`. (S17-05)
- [x] T1715 – Add Selenium/Web tests covering query-parameter deep links and history behaviour for `/ui/console`, ensure they fail prior to implementation. (S17-04)
- [x] T1716 – Implement query-parameter state management + history handling for the console, run `./gradlew :rest-api:test` to verify green. (S17-04)
- [x] T1717 – Refresh docs/knowledge map and telemetry notes for the stateful URL behaviour, rerun `./gradlew spotlessApply check`. (S17-05)
- [x] T1718 – Add failing Selenium coverage asserting the evaluation result no longer renders a Sanitized row. (_2025-10-04_) (S17-02)
- [x] T1719 – Remove the Sanitized row from evaluation results, update Selenium expectations, and rerun `./gradlew :rest-api:test spotlessApply check`. (_2025-10-04_) (S17-02)
- [x] T1720 – Update Selenium coverage for seeding status placement beneath the seed control with persistent hint text. (_2025-10-04_) (S17-03)
- [x] T1721 – Implement seeding status layout/script adjustments and rerun `./gradlew :rest-api:test spotlessApply check`. (_2025-10-04_) (S17-03)
- [x] T1722 – Add Selenium coverage asserting zero-added seeding responses apply the warning styling. (_2025-10-04_) (S17-03)
- [x] T1723 – Implement warning styling for zero-added seeding results, rerun `./gradlew :rest-api:test spotlessApply check`. (_2025-10-04_) (S17-03)
- [x] T1724 – Relocate the shared console stylesheet to `/ui/console/console.css`, update templates/JS/tests, and rerun `./gradlew spotlessApply check`. (_2025-10-15_) (S17-05)

Update this checklist as increments progress; keep tests ahead of implementation per SDD cadence.
