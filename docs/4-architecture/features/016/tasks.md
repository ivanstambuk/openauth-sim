# Feature 016 – Task Checklist

_Status: In Progress_
_Last updated: 2025-10-04_

## Tasks (≤30 min each)
- [x] T1601 – Extend Selenium/system suite with failing scenarios for replay navigation + stored credential success path. (_2025-10-03_) (S16-01)
- [x] T1602 – Add Selenium/system coverage for inline replay success and validation-error paths (expect red until implementation). (_2025-10-03_) (S16-01)
- [x] T1603 – Introduce MockMvc/WebTestClient tests for replay controller forwarding, covering stored/inline payloads and error handling. (_2025-10-03_) (S16-02)
- [x] T1604 – Implement replay screen templates, controller actions, and REST wiring to satisfy stored/inline flows and make tests green. (_2025-10-03_) (S16-02)
- [x] T1605 – Add telemetry instrumentation + unit tests verifying new fields (mode/outcome/fingerprint) for UI replay events. (_2025-10-03_) (S16-03)
- [x] T1606 – Update operator how-to guide and telemetry reference docs to describe UI replay usage. (_2025-10-03_) (S16-04)
- [x] T1607 – Run `./gradlew :rest-api:test :rest-api:systemTest spotlessApply check`, self-review diffs, and capture notes in the feature plan. (_2025-10-03_) (S16-02)
- [x] T1608 – Add Selenium assertion for inline preset dropdown behaviour (expected to fail before implementation). (_2025-10-03_) (S16-01)
- [x] T1609 – Surface inline presets on replay view (controller/template/JS) and rerun Selenium + `./gradlew spotlessApply check`. (_2025-10-03_) (S16-01)
- [x] T1610 – Hide/disable replay sample presets when stored mode is active and update Selenium coverage. (_2025-10-03_) (S16-01)
- [x] T1611 – Add expected OTP values to inline policy presets and document the change. (_2025-10-03_) (S16-01)
- [x] T1612 – Update replay JS + Selenium suites so selecting a preset populates OTP and succeeds on submission; rerun `./gradlew :rest-api:test spotlessApply check`. (_2025-10-03_) (S16-01)
- [x] T1613 – Align replay result card styling with evaluation console (status emphasis and telemetry list) plus UI test refinements. (_2025-10-03_) (S16-01)

Update this checklist as increments complete; keep tests ahead of implementation to maintain SDD cadence.
- [x] T1614 – Add failing Selenium coverage asserting inline auto-fill button populates preset data in replay mode. (_2025-10-04_) (S16-01)
- [x] T1615 – Implement inline auto-fill control (template + JS), ensure presets apply suite/secret/context/OTP, and rerun Selenium + `./gradlew spotlessApply check`. (_2025-10-04_) (S16-01)
- [x] T1616 – Update operator UI docs/telemetry guidance for the new auto-fill feature and rerun quality gate commands. (_2025-10-04_) (S16-04)
- [x] T1617 – Remove replay inline auto-fill button, adjust templates/JS/tests so preset selection alone populates fields. (_2025-10-04_) (S16-01)
- [x] T1618 – Update documentation/spec/knowledge map for the removal and rerun affected tests + `./gradlew spotlessApply check`. (_2025-10-04_) (S16-04)
- [x] T1619 – Drop the replay "Mode" metadata row (keep reason/outcome), update Selenium/UI expectations, and rerun ./gradlew :rest-api:test spotlessApply check. (_2025-10-04_) (S16-01)
- [ ] T1620 – Remove the stored replay “Load sample data” control, auto-fill context on credential selection, refresh Selenium/MockMvc coverage, and rerun `./gradlew spotlessApply check`. (_Pending_) (S16-04)
