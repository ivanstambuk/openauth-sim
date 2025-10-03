# Feature 011 – Reflection Policy Hardening Tasks

_Status: Complete_
_Last updated: 2025-10-02_

## Tasks
| ID | Task | Related Requirements | Status |
|----|------|----------------------|--------|
| T1101 | Catalogue current reflection usage across modules and log findings in the feature plan. | REF-001 | ✅ (2025-10-01) |
| T1102 | Add failing ArchUnit enforcement test that flags `java.lang.reflect` imports outside approved packages. | REF-004 | ✅ (2025-10-01) |
| T1103 | Add failing test/validation for regex-based Gradle check catching reflective API names in strings. | REF-004 | ✅ (2025-10-01) |
| T1104 | Implement ArchUnit rule and integrate into `core-architecture-tests`. | REF-004 | ✅ (2025-10-01) |
| T1105 | Implement Gradle regex scan task and wire into `qualityGate` with necessary allowlist. | REF-004 | ✅ (2025-10-01) |
| T1106 | Introduce explicit CLI OCRA collaborator interface (tests first) replacing reflective access in CLI tests. | REF-001, REF-002 | ✅ (2025-10-01) |
| T1107 | Introduce structured DTO/accessors for maintenance CLI parsing (tests first) and remove test reflection. | REF-001, REF-002 | ✅ (2025-10-01) |
| T1108 | Refactor REST OCRA services/tests to remove reflection via service seams or DTO exposure. | REF-001, REF-002 | ✅ (2025-10-01) |
| T1109 | Replace reflection in core tests with direct APIs or fixtures, adding tests that document expected seams. | REF-001, REF-002 | ✅ (2025-10-01) |
| T1110 | Update `AGENTS.md` with anti-reflection guidance and link to spec/guard instructions. | REF-003 | ✅ (2025-10-01) |
| T1111 | Update knowledge map/roadmap entries, run `./gradlew spotlessApply check`, and record outcomes in plan notes. | REF-001–REF-004 | ✅ (2025-10-01) |

Ensure each task completes within ≤10 minutes and prioritise test-first execution where applicable.
