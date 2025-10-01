# Feature 013 – Java 17 Language Enhancements Tasks

_Status: Complete_
_Last updated: 2025-10-01_

## Tasks
| ID | Task | Related Requirements | Status |
|----|------|----------------------|--------|
| T1301 | Add roadmap entry and baseline notes for Feature 013. | J17-CLI-001, J17-REST-002, J17-DOC-003 | ✅ (2025-10-01) |
| T1302 | Document ≤10 minute increments in this task list and verify no open questions needed. | J17-CLI-001, J17-REST-002, J17-DOC-003 | ✅ (2025-10-01) |
| T1303 | Execute analysis gate checklist; capture tooling readiness details in feature plan. | J17-CLI-001–J17-DOC-003 | ✅ (2025-10-01) |
| T1304 | Write failing CLI test (or adjust existing ones) expecting sealed hierarchy behaviour, then implement sealed declaration. | J17-CLI-001 | ✅ (2025-10-01) |
| T1305 | Update REST evaluation service tests to assume sealed request variants, refactor implementation accordingly. | J17-REST-002 | ✅ (2025-10-01) |
| T1306 | Update REST verification service tests to assume sealed request variants, refactor implementation accordingly. | J17-REST-002 | ✅ (2025-10-01) |
| T1307 | Replace OpenAPI example strings with text blocks and validate via snapshot/unit tests. | J17-DOC-003 | ✅ (2025-10-01) |
| T1308 | Run `./gradlew spotlessApply check` (and `qualityGate` if time permits), update knowledge map/notes, and prepare commit summary. | J17-NFR-001 | ✅ (2025-10-01) |
| T1309 | Audit CLI modules for additional command hierarchies that warrant sealing and document findings. | J17-CLI-001 | ✅ (2025-10-01) |
| T1310 | Audit REST controllers for escaped JSON example strings and confirm text block adoption policy. | J17-DOC-003 | ✅ (2025-10-01) |
