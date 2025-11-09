# Feature 041 Tasks – Operator Console JavaScript Modularization & Test Harness

_Status: Not started_  
_Last updated: 2025-11-07_

- [ ] **T4101 – Console asset inventory & extraction notes:** Catalogue every inline script fragment (OCRA evaluate/replay, any residual preset loaders) and capture the extraction plan inside the Feature 041 spec appendix; update roadmap + `_current-session.md` with findings.
- [ ] **T4102 – Shared Node harness scaffolding (S41-02, S41-06):** Create `rest-api/src/test/javascript/support/` utilities (DOM stubs, SecretFieldBridge mocks, fetch spies), port existing EMV tests to the harness, and add contributing notes for running the suites locally.
- [ ] **T4103 – HOTP/TOTP/FIDO2 coverage wave (S41-03, S41-04):** Extract any leftover inline helpers, add Node test suites that exercise mode toggles, preset hydration, seeding, preview windows, verbose trace wiring, and secret field messaging for HOTP/TOTP/FIDO2; refresh Selenium assertions only where expectations change.
- [ ] **T4104 – OCRA controller extraction + tests (S41-05):** Move OCRA evaluate/replay scripts into standalone assets, expose controller factories, and add Node tests covering credential directory fetch flows, preset hydration, inline validation, and replay toggles.
- [ ] **T4105 – Gradle integration & documentation (S41-07):** Replace `emvConsoleJsTest` with a consolidated `operatorConsoleJsTest`, wire it under `check`, document usage in README/CONTRIBUTING, and update roadmap/knowledge-map entries.
- [ ] **T4106 – Governance & future-protocol checklist (S41-08):** Extend this tasks file with onboarding steps for future protocols, record Implementation Drift Gate expectations, and keep the spec in Draft status until governance confirms ongoing compliance.
