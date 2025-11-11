# Feature Catalogue Migration Plan

_Last updated: 2025-11-11_

## Context & Objectives
- Adopt the Spec-as-Source workflow: every feature spec must fully describe behaviour, contracts, telemetry, UI states, fixtures, and machine-readable DSL so code/tests/docs can be regenerated from it.
- Collapse the legacy numeric feature scatter (001–041 + thematic folders) into a clean, vertically sliced catalogue starting at 001, where each feature owns the entire stack for its protocol or platform objective.
- Remove `_archive/` and any split between “protocols” vs “platform”; the file/folder names themselves carry the numbering.
- Normalize all specs/plans/tasks to the latest templates (functional vs non-functional requirements, interface catalogues, scenario tracking, templated task entries). No information loss is permitted.
- Keep migration auditable: track per-feature moves, directory renames, and doc updates inside this plan before executing them.
- **Directive (2025-11-10):** Pause all directory renumbering work; focus exclusively on migrating every feature’s spec/plan/tasks to the refreshed templates before touching folder structures again.

## Target Feature List (draft)
| New ID | Working Title | Scope Summary | Legacy Feature IDs feeding it |
|--------|---------------|---------------|--------------------------------|
| 001 | HOTP Simulator & Tooling | Core HOTP flows, CLI/REST/UI, telemetry, fixtures | 022, parts of 001/002/003/004/005/008/009 |
| 002 | TOTP Simulator & Tooling | TOTP presets + UI/CLI/REST parity | 023, 017, 020, 021 fragments |
| 003 | OCRA Simulator & Replay | All OCRA stacks (core, CLI/REST/UI, replay, telemetry) | 001,003,004,005,006,008,009,016,018 |
| 004 | FIDO2/WebAuthn Assertions & Attestations | Assertion + attestation workflows, fixtures | 024,026 |
| 005 | EMV/CAP Evaluation & Replay | Current Feature 039 scope | 039 |
| 006 | EUDIW OpenID4VP Simulator | Current Feature 040 scope | 040 |
| 007 | EUDIW ISO/IEC 18013-5 (mdoc PID) Simulator | Placeholder for future ISO/mDoc work | (new) |
| 008 | EUDIW SIOPv2 Wallet Simulator | Placeholder for SIOPv2 | (new) |
| 009 | Operator Console Infrastructure | Shared console shell, JS modules, harness | 017,020,021,025,033-038,041 fragments |
| 010 | Documentation & Knowledge Automation | How-to guides, knowledge map, roadmap automation | 007,010 |
| 011 | Governance & Workflow Automation | Constitution, AGENTS, git hooks, analysis gates | 019,032 |
| 012 | Core Cryptography & Persistence | Shared credential stores, persistence adapters | 001,002,027,028 |
| 013 | Toolchain & Quality Platform | Lint/formatter/CI rules (PMD, SpotBugs, Gradle) | 011-015,029,030,031 |

> Final names/IDs can be adjusted later, but commits should follow this numbering to avoid rework.

## Template Standards (already applied globally)
1. **Spec template (docs/templates/feature-spec-template.md)**
   - Metadata table
   - Separate Functional Requirements (FRx) and Non-Functional Requirements (NFRx) with `**Requirement:**` bullet, success/validation/failure, telemetry, source
   - UI mock-ups
   - Branch & Scenario Matrix (success/validation/failure subsections + scenario table)
   - Test Strategy
   - Telemetry & Observability
   - Documentation Deliverables
   - Fixtures & Sample Data
   - Interface & Contract Catalogue (Domain Objects, API Routes, CLI Commands, Telemetry Events, Fixtures, UI States)
   - Spec DSL (YAML-style, reusing IDs)
   - Appendix (Acceptance Criteria, references, follow-ups)
2. **Plan template (docs/templates/feature-plan-template.md)**
   - Vision, Scope, Dependencies
   - Increment Map (≤90 min)
   - Scenario Tracking table
   - Assumptions & Risks
   - Quality & Tooling Gates
   - Analysis Gate + Implementation Drift Gate
   - Exit Criteria + Follow-ups
3. **Tasks template (docs/templates/feature-tasks-template.md)**
   - Checklist entries with `_Intent_`, `_Verification commands_`, `_Notes_`
   - Verification log + notes sections as needed
4. **Template sweep status (2025-11-11):** Every feature folder (001–041) now follows these templates; all new work must start from the refreshed spec/plan/tasks structure before implementation begins.

## Migration Steps
1. **Template Alignment (DONE – 2025-11-11)**
   - All feature folders (001–041) now use the refreshed spec/plan/tasks templates; no further migration work remains for this step.
   - Future features must start with the templates before planning/coding; only incremental template updates (if the global templates change) remain in scope.

2. **Directory Renumbering (TODO)**
   - For each target ID, create `docs/4-architecture/features/<NNN>/` with spec/plan/tasks per template.
   - Copy content from legacy features into the new folder, merging multiple old features where needed.
   - Update all cross-references (roadmap, knowledge map, runbooks, tasks) to the new IDs.
   - Delete old folders only after references resolve and git history captures the move (no `_archive/`).

3. **Spec Migration (per feature)**
   - Move clarifications/goals into new template sections.
   - Break requirements into FR/NFR entries with sources.
   - Document UI states, API routes, CLI commands, telemetry, fixtures, interface catalogue, Spec DSL.
   - Add Documentation Deliverables, Telemetry & Observability, Fixtures sections.
   - Merge any “profile/options/contract” appendices into the new structure.

4. **Plan Migration (per feature)**
   - Recast increment history into the Increment Map + Scenario Tracking table.
   - Capture assumptions/risks and quality gates.
   - Preserve Implementation Drift Gate notes with a dated bullet list.

5. **Tasks Migration (per feature)**
   - Format each task with `_Intent`, `_Verification commands`, `_Notes`.
   - Ensure references to FR/NFR/Scenario IDs remain intact.
   - Keep historical notes under `_Notes` (use blockquotes for long updates).

6. **Global Doc Sync**
   - Update `docs/4-architecture/features/README.md` with the new numbering scheme and migration status.
   - Refresh `docs/4-architecture/roadmap.md`, `docs/_current-session.md`, and `docs/4-architecture/knowledge-map.md` to point to the new IDs.
   - Add a short “Renumbering completed” note in `docs/5-operations/session-quick-reference.md` once the move is done.

## Execution Order
1. **Lock renumbering prerequisites.** Finalise the target mapping (protocol features 001–007 first, then platform features 009–013), confirm with the owner which folders move together, and document the plan/approvals in `docs/_current-session.md` plus the roadmap before touching the tree.
2. **Perform directory renumbering.** For each logical batch, create the new `docs/4-architecture/features/<NNN>/` directories, copy specs/plans/tasks (already templated), and update cross-references (roadmap, knowledge map, runbooks, feature references) in the same change.
3. **Retire legacy folders safely.** Once references resolve and history captures the move, delete the old directories (no `_archive/`) and note the removal in the migration plan.
4. **Verify and log.** After each renumbering batch, rerun the agreed verification commands (spotless/check + affected module tests), update `docs/_current-session.md`, and append a short summary in this plan so reviewers can trace the sequence.

## Open Questions / Decisions Needed
- Confirm final names/IDs for platform features (009–013) before directory moves begin.
- Confirm whether historical feature folders (e.g., 001–041) should be deleted immediately after copy or preserved temporarily in a staging branch.
- Determine if additional Spec DSL fields are required for future automation (e.g., telemetry enums, fixture provenance).
