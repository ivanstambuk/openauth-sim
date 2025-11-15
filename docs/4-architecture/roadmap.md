# Implementation Roadmap

_Last updated: 2025-11-15_

This roadmap mirrors the feature catalogue (001–013) plus the reserved placeholder slots for the next wave of work. Each feature row below maps directly to `docs/4-architecture/features/<NNN>/{spec,plan,tasks}.md`, keeping the roadmap aligned with the source documents instead of duplicating legacy numbering or migration callouts.

## Guiding Principles

- Specifications drive every increment; update the spec before refreshing plan/tasks or writing code.
- Keep increments small and verifiable (≤90 minutes of planned effort) with `./gradlew spotlessApply check` after each slice.
- Preserve traceability by logging hook guard + verification commands in `docs/_current-session.md`.

## Features (001–013)

| ID | Name | Status | Scope Snapshot | References |
|----|------|--------|----------------|------------|
| 001 | HOTP Simulator & Tooling | Complete | RFC 4226 HOTP across core, application, CLI/REST, and operator console with telemetry parity. | [spec](features/001/spec.md) · [plan](features/001/plan.md) · [tasks](features/001/tasks.md) |
| 002 | TOTP Simulator & Tooling | Complete | RFC 6238 TOTP flows (stored/inline/replay) plus fixtures, presets, and console helpers. | [spec](features/002/spec.md) · [plan](features/002/plan.md) · [tasks](features/002/tasks.md) |
| 003 | OCRA Simulator & Replay | Complete | Unified OCRA domain, replay tooling, and UI/REST/CLI coverage replacing the legacy multi-feature set. | [spec](features/003/spec.md) · [plan](features/003/plan.md) · [tasks](features/003/tasks.md) |
| 004 | FIDO2/WebAuthn Assertions & Attestations | Complete | Combined assertion + attestation simulator with deterministic fixtures, trust-anchor handling, and operator guidance. | [spec](features/004/spec.md) · [plan](features/004/plan.md) · [tasks](features/004/tasks.md) |
| 005 | EMV/CAP Simulation Services | Complete | End-to-end EMV CAP Identify/Respond/Sign flows with MapDB seeding, shared VerboseTraceConsole/includeTrace parity (Evaluate + Replay), replay mismatch diagnostics (TE‑005‑05 hashed OTP + mismatchReason telemetry), and operator presets. | [spec](features/005/spec.md) · [plan](features/005/plan.md) · [tasks](features/005/tasks.md) |
| 006 | EUDIW OpenID4VP Simulator | In progress | HAIP-aligned remote OpenID4VP verifier/wallet simulator covering SD-JWT VCs and mdoc payloads. | [spec](features/006/spec.md) · [plan](features/006/plan.md) · [tasks](features/006/tasks.md) |
| 007 | EUDIW mdoc PID Simulator | Placeholder | ISO/IEC 18013-5 mdoc PID wallet simulator sharing fixtures with Feature 006 (scope pending owner approval). | [spec](features/007/spec.md) · [plan](features/007/plan.md) · [tasks](features/007/tasks.md) |
| 008 | EUDIW SIOPv2 Wallet Simulator | Placeholder | Deterministic SIOPv2 wallet experience for mixed SD-JWT + mdoc payloads across REST/CLI/UI. | [spec](features/008/spec.md) · [plan](features/008/plan.md) · [tasks](features/008/tasks.md) |
| 009 | Operator Console Infrastructure | Complete | Consolidates all operator-console UI work (tabs, verbose trace dock, fixture presets) under one governance hub. | [spec](features/009/spec.md) · [plan](features/009/plan.md) · [tasks](features/009/tasks.md) |
| 010 | Documentation & Knowledge Automation | Complete | Centralises docs/how-to automation, knowledge map upkeep, and documentation quality gates. | [spec](features/010/spec.md) · [plan](features/010/plan.md) · [tasks](features/010/tasks.md) |
| 011 | Governance & Workflow Automation | Complete | Owns AGENTS.md, constitution alignment, managed hooks, gitlint policy, and analysis-gate guidance. | [spec](features/011/spec.md) · [plan](features/011/plan.md) · [tasks](features/011/tasks.md) |
| 012 | Core Cryptography & Persistence | Complete | Shared persistence defaults, cache tuning, telemetry contracts, maintenance helpers, and encryption guidance. | [spec](features/012/spec.md) · [plan](features/012/plan.md) · [tasks](features/012/tasks.md) |
| 013 | Toolchain & Quality Platform | Complete | Aggregates CLI exit harnesses, reflection policy enforcement, quality gates, and Gradle/Spotless automation. | [spec](features/013/spec.md) · [plan](features/013/plan.md) · [tasks](features/013/tasks.md) |

## Placeholder Queue (014+)

The next wave of features keeps their numbering reserved so new specs can slot in without another renumbering effort.

- **Features 014–018 – Credential expansion backlog.** These slots cover future credential families (for example, additional OTP profiles, passkey portability, or EMV follow-ons). When one of these efforts graduates from ideation, create `docs/4-architecture/features/014/` (or the matching ID) directly from the templates and capture the clarifications there; until then, record open questions or research stubs in `docs/4-architecture/open-questions.md` rather than a parking-lot directory.
- **Features 019–022 – Next-gen simulator research.** Reserved for protocol simulators that extend beyond the current OTP/WebAuthn/EUDIW footprint. Seed notes via the same spec/plan/tasks pipeline once owners prioritise an item so the numbering remains contiguous without separate holding folders.
- **Features 023+ – OCRA/legacy deep dives.** Historic artefacts that still require parity checks now live only in Git history; reintroduce them by authoring fresh specs under the next free feature ID instead of restoring the retired `ocra-simulator/` staging area.

Update this document whenever a feature’s status changes or a placeholder graduates into an active spec so the roadmap always reflects the consolidated numbering scheme.
