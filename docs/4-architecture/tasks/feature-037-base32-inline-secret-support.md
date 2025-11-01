# Feature 037 Tasks – Base32 Inline Secret Support

_Status: Complete_  
_Last updated: 2025-11-01_

- [x] T3701 – Implement Base32→hex encoding helper with unit coverage and wire it into HOTP/TOTP inline DTO validation scaffolding.  
- [x] T3702 – Extend HOTP/TOTP/OCRA REST evaluate/replay services to accept Base32 secrets; update controller tests and OpenAPI snapshots.  
- [x] T3703 – Introduce Base32 Picocli options for HOTP/TOTP/OCRA inline commands with integration tests.  
- [x] T3704 – Update operator console inline panels for all three protocols, add Base32↔hex synchronisation, and refresh Selenium coverage (`./gradlew --no-daemon :rest-api:test :ui:test`, 2025-10-31).  
- [x] T3705 – Sync documentation, knowledge map, and `_current-session.md`; rerun full quality gate commands (`./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`, 2025-10-31).
- [x] T3706 – Consolidate UI shared secret input into a single textarea with Hex/Base32 mode toggle, update validation + Selenium coverage, and re-run `./gradlew --no-daemon :rest-api:test :ui:test` (2025-11-01).
- [x] T3707 – Collapse shared-secret hint/warning copy into a single dynamic message that displays the conversion hint by default and swaps to validation errors on demand (`./gradlew --no-daemon :rest-api:test :ui:test`, 2025-11-01).
