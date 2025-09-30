# Feature 007 – Operator-Facing Documentation Suite Tasks

_Status: Draft_
_Last updated: 2025-09-30_

## Execution Notes
- Specification: `docs/4-architecture/specs/feature-007-operator-docs.md`.
- Keep each task ≤10 minutes and commit after `./gradlew spotlessApply check` passes.
- Use existing CLI/REST artefacts to verify command syntax and endpoint snapshots before finalising prose.
- Update roadmap and knowledge map once documentation is published.

## Tasks
| ID | Task | Related Requirements | Status |
|----|------|----------------------|--------|
| D071 | Close clarification loop, draft doc outlines, remove resolved entries from open questions | Spec alignment, Prep | ✅ |
| D072 | Author Java operator integration guide under `docs/2-how-to/` with runnable examples | FR1 | ✅ |
| D073 | Create CLI operations guide under `docs/2-how-to/`, covering all commands with examples | FR2 | ✅ |
| D074 | Replace REST evaluation how-to with comprehensive REST operations guide covering every endpoint and Swagger UI usage | FR3 | ✅ |
| D075 | Update `README.md` with current capability summary + Swagger UI link; remove future-work placeholders | FR4 | ✅ |
| D076 | Run `./gradlew spotlessApply check` and update roadmap/knowledge map links as needed | FR5, Verification | ✅ |

Mark tasks complete as increments ship.
