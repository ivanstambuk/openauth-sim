# Feature Artifact Layout

Each feature now owns a dedicated directory under ``docs/4-architecture/features`/<NNN>/` (three-digit feature id). Every folder contains:

- `spec.md` – the authoritative specification.
- `plan.md` – the implementation plan / roadmap slice for that feature.
- `tasks.md` – the execution checklist with scenario references.
- Optional supporting notes (for example, `feature-001-ocra-data-model.md`).

When creating a new feature:

1. Add `spec.md`, `plan.md`, and `tasks.md` to ``docs/4-architecture/features`/<NNN>/`.
2. Reference the files via the canonical paths ``docs/4-architecture/features`/<NNN>/spec.md`, etc.
3. Keep scenario IDs stable across all three artifacts.

## Renumbering Note (2025-11-11)
- The catalogue is transitioning to 13 vertically sliced features (001–013). During the migration, some legacy artifacts
  remain under ``docs/4-architecture/features`/<NNN>/legacy/<old-id>/` (currently Features 001–008) so history is preserved
  without keeping the old top-level directories. Features 009–013 have finished their Phase 2 rewrites and no longer ship
  `legacy/` subdirectories; consult Git history for their archived content.
- Batch P1 moved HOTP/TOTP/OCRA content into Features 001/002/003. Reference those specs for the authoritative
  requirements; consult the `legacy/` subdirectories only for historical context.
- Batch P2 (in progress – 2025-11-11) freed Features 004–006 for WebAuthn, EMV/CAP, and EUDIW OpenID4VP by relocating the
  previous OCRA/REST specs into `Feature 003/legacy/00x/`. WebAuthn’s Feature 004 now embeds the legacy Feature 024/026
  documents, Feature 005 hosts the former Feature 039 EMV/CAP stack, and Feature 006 hosts the former Feature 040
  OpenID4VP spec. Features 007/008 now carry placeholders for the upcoming EUDIW mdoc PID and SIOPv2 wallet work, while
  the original documentation/quality artifacts live under `docs/4-architecture/features/new-010/legacy/007/` and
  `.../legacy/008/` ahead of the Feature 010 consolidation.
- Batch P3 (2025-11-11) closed out the Operator & Platform Infrastructure sweep. Feature 009 now owns the consolidated
  operator-console scope, Feature 010 carries documentation & knowledge automation, Feature 011 governs governance/runbooks
  and hooks, Feature 012 centralises cryptography/persistence docs, and Feature 013 aggregates toolchain + quality
  automation. Their former staging directories (`operator-console/`, `docs-and-quality/`, `platform-foundations/`,
  `new-010/`, `new-012/`) were removed after the Phase 2 verification gate; consult Git history for the
  command log and historical context.
- The placeholder parking lots `features/credential-expansion/`, `features/next-gen-simulators/`, and
  `features/ocra-simulator/` were retired on 2025-11-11. When Feature 014+ work begins, create the final directory
  (`features/014/`, `features/015/`, etc.) from the templates and document clarifications there so numbering stays
  consistent without separate holding areas.

The legacy ``docs/4-architecture/specs`/`, `tasks/`, and `feature-plan-*.md` files have been relocated here to keep the top-level directory manageable.
