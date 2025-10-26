# Feature 020 – Task Checklist

_Status: Draft_
_Last updated: 2025-10-04_

## Tasks (≤30 min each)
- [x] T2001 – Add failing Selenium/DOM assertions for the expanded protocol tab order and placeholder panels; run `./gradlew :rest-api:test` to confirm failures. (_2025-10-04 – test suite now fails on missing HOTP/TOTP/EUDIW tabs._)
- [x] T2002 – Implement HOTP/TOTP/EUDIW tab skeleton in the Thymeleaf template and console script, update styles/tests, and rerun `./gradlew :rest-api:test`. (_2025-10-04 – template/JS updated; Selenium suite now green._)
- [x] T2003 – Update roadmap (and knowledge map if needed) with discrete HOTP/TOTP/EUDIW workstreams; rerun `./gradlew spotlessApply check`. (_2025-10-04 – roadmap/knowledge map synced; full build green._)

Update this checklist as increments complete; ensure tests lead each implementation step.
