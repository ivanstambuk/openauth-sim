# Feature 016 – Task Checklist

_Status: Draft_
_Last updated: 2025-10-03_

## Tasks (≤10 min each)
- [x] T1601 – Extend Selenium/system suite with failing scenarios for replay navigation + stored credential success path. (_2025-10-03_)
- [x] T1602 – Add Selenium/system coverage for inline replay success and validation-error paths (expect red until implementation). (_2025-10-03_)
- [ ] T1603 – Introduce MockMvc/WebTestClient tests for replay controller forwarding, covering stored/inline payloads and error handling.
- [ ] T1604 – Implement replay screen templates, controller actions, and REST wiring to satisfy stored/inline flows and make tests green.
- [ ] T1605 – Add telemetry instrumentation + unit tests verifying new fields (mode/outcome/fingerprint) for UI replay events.
- [ ] T1606 – Update operator how-to guide and telemetry reference docs to describe UI replay usage.
- [ ] T1607 – Run `./gradlew :rest-api:test :rest-api:systemTest spotlessApply check`, self-review diffs, and capture notes in the feature plan.

Update this checklist as increments complete; keep tests ahead of implementation to maintain VDD cadence.
