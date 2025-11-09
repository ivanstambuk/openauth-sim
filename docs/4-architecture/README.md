# Architecture (Draft)

Initial architecture targets:

- **Context (C4 Level 1):** Emulator runtime as a single node interacting with local MapDB storage and future clients (CLI, REST, UI).
- **Containers:**
  - `core` JVM library
  - Command-line interface (Picocli)
  - Spring Boot REST service
  - Server-rendered UI consuming REST API
- **Components:** Credential persistence adapters, crypto operations, protocol emulators.

Upcoming tasks:

1. Publish C4 diagrams (`docs/_assets`) once CLI/REST wiring starts.
2. Document MapDB on-disk format and caching behaviour.
3. Capture performance budgets (targeting 1000+ credential lookups/sec).

## Templates

Use the shared templates under `docs/templates/` whenever you add or modify documentation artifacts:

- `feature-spec-template.md` – canonical schema for feature specifications (metadata table, clarifications, requirements, mock-ups, test strategy).
- `feature-plan-template.md` – increment planner with drift-gate notes, scope alignment, and ≤90-minute slices.
- `feature-tasks-template.md` – per-feature checklist capturing task intent plus verification commands.
- `adr-template.md` – architectural decision records.
- `how-to-template.md` – operational playbooks/how-to guides.
- `runbook-template.md` – incident/runbook documentation.

## Feature Artifact Layout

Every feature owns a dedicated directory under `docs/4-architecture/features/<NNN>/` (three-digit feature id). Each folder contains:

- `spec.md` – the authoritative specification.
- `plan.md` – the implementation plan aligned with the roadmap slice.
- `tasks.md` – the execution checklist, including scenario IDs and verification commands.
- Optional supporting notes (for example, protocol data models or UI sketches specific to that feature).

When creating or updating a feature, keep the trio in sync inside the same directory and update references to the canonical paths above instead of the legacy `specs/`, `feature-plan-*.md`, or `tasks/` locations.
