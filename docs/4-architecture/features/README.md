# Feature Artifact Layout

Each feature now owns a dedicated directory under `docs/4-architecture/features/<NNN>/` (three-digit feature id). Every folder contains:

- `spec.md` – the authoritative specification.
- `plan.md` – the implementation plan / roadmap slice for that feature.
- `tasks.md` – the execution checklist with scenario references.
- Optional supporting notes (for example, `feature-001-ocra-data-model.md`).

When creating a new feature:

1. Add `spec.md`, `plan.md`, and `tasks.md` to `docs/4-architecture/features/<NNN>/`.
2. Reference the files via the canonical paths `docs/4-architecture/features/<NNN>/spec.md`, etc.
3. Keep scenario IDs stable across all three artifacts.

The legacy `docs/4-architecture/specs/`, `tasks/`, and `feature-plan-*.md` files have been relocated here to keep the top-level directory manageable.
