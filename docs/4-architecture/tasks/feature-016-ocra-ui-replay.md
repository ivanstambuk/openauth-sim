# Feature 016 – Task Checklist

_Status: Complete_
_Last updated: 2025-10-03_

## Tasks (≤10 min each)
- [x] T1601 – Extend Selenium/system suite with failing scenarios for replay navigation + stored credential success path. (_2025-10-03_)
- [x] T1602 – Add Selenium/system coverage for inline replay success and validation-error paths (expect red until implementation). (_2025-10-03_)
- [x] T1603 – Introduce MockMvc/WebTestClient tests for replay controller forwarding, covering stored/inline payloads and error handling. (_2025-10-03_)
- [x] T1604 – Implement replay screen templates, controller actions, and REST wiring to satisfy stored/inline flows and make tests green. (_2025-10-03_)
- [x] T1605 – Add telemetry instrumentation + unit tests verifying new fields (mode/outcome/fingerprint) for UI replay events. (_2025-10-03_)
- [x] T1606 – Update operator how-to guide and telemetry reference docs to describe UI replay usage. (_2025-10-03_)
- [x] T1607 – Run `./gradlew :rest-api:test :rest-api:systemTest spotlessApply check`, self-review diffs, and capture notes in the feature plan. (_2025-10-03_)
- [x] T1608 – Add Selenium assertion for inline preset dropdown behaviour (expected to fail before implementation). (_2025-10-03_)
- [x] T1609 – Surface inline presets on replay view (controller/template/JS) and rerun Selenium + `./gradlew spotlessApply check`. (_2025-10-03_)
- [x] T1610 – Hide/disable replay sample presets when stored mode is active and update Selenium coverage. (_2025-10-03_)
- [x] T1611 – Add expected OTP values to inline policy presets and document the change. (_2025-10-03_)
- [x] T1612 – Update replay JS + Selenium suites so selecting a preset populates OTP and succeeds on submission; rerun `./gradlew :rest-api:test spotlessApply check`. (_2025-10-03_)
- [x] T1613 – Align replay result card styling with evaluation console (status emphasis and telemetry list) plus UI test refinements. (_2025-10-03_)

Update this checklist as increments complete; keep tests ahead of implementation to maintain VDD cadence.
