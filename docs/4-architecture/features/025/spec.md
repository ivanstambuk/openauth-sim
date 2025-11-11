# Feature 025 – Sample Vector Label Harmonization

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-10 |
| Owners | Ivan (project owner) |
| Linked plan | `docs/4-architecture/features/025/plan.md` |
| Linked tasks | `docs/4-architecture/features/025/tasks.md` |
| Roadmap entry | #21 – Operator console UX polish |

## Overview
Align every operator console “Load a sample vector” dropdown so presets share a concise `<scenario – key attributes>`
pattern, omit redundant wording, and keep seeded/inline coverage identical across HOTP, TOTP, OCRA, and FIDO2. The
change is documentation- and UI-focused, ensuring Selenium coverage, telemetry redaction, and seeded credential
catalogues stay consistent with the renamed labels.

## Clarifications
- 2025-10-12 – Remove the literal phrase “sample vector” from dropdown options; rely on surrounding copy for context (user approved Option A).
- 2025-10-12 – HOTP inline presets must cover RFC vectors plus seeded credentials across 6- and 8-digit SHA-1/256/512 variants; stored seeding must expose the same catalogue (user directive).
- 2025-10-12 – Inline dropdown labels drop any “Seeded credential” prefixes/suffixes; keep attribute-only strings (user directive).
- 2025-10-12 – OCRA presets sourced from RFC 6287 append “(RFC 6287)” to inline and stored labels, while the draft-only `OCRA-1:HOTP-SHA256-6:C-QH64` entry remains untagged (user request).

## Goals
- G-025-01 – Standardize preset labels and placeholders across HOTP, TOTP, OCRA, and FIDO2 operator panels.
- G-025-02 – Ensure HOTP inline/stored dropdowns expose the full seeded credential matrix (SHA algorithm × digit count).
- G-025-03 – Preserve RFC attribution for OCRA presets via succinct label suffixes.
- G-025-04 – Keep tests, docs, and roadmap/session notes synchronized with the renamed labels.

## Non-Goals
- N-025-01 – No new sample data beyond harmonizing existing presets.
- N-025-02 – CLI/REST payload naming remains unchanged.
- N-025-03 – Telemetry event schemas stay as-is (only label strings change).

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-025-01 | Operator console dropdowns display presets as `<scenario – key attributes>` with no “sample vector” wording; placeholders/hints match the copy. | HOTP/TOTP/OCRA/FIDO2 templates and JS render the new labels with matching Selenium snapshots. | UI validation checks for missing labels, placeholder mismatches, or regressions in accessibility text. | Dropdown renders stale copy or empty options. | Existing operator UI telemetry keeps sanitized label strings. | Clarifications 2025-10-12.
| FR-025-02 | HOTP inline and stored presets cover SHA-1/256/512 across 6/8 digits with matching seeded credentials. | Sample data/helpers expose six presets and Selenium verifies both dropdowns match. | Integration tests ensure stored credentials exist for each inline preset. | Missing preset or seeded credential mismatch. | No telemetry change; ensure credential IDs remain hashed. | Clarifications 2025-10-12.
| FR-025-03 | RFC-backed OCRA inline and stored presets append “(RFC 6287)” while draft-only entries stay untouched. | UI + docs show suffixes exactly where required. | Tests compare label lists; docs outline RFC provenance. | RFC suffix absent or applied to draft entry. | Telemetry sanitized fields remain unchanged. | Clarifications 2025-10-12.
| FR-025-04 | Documentation, roadmap/session notes, and verification logs capture the finished rename with a green `./gradlew spotlessApply check`. | Operator how-to, roadmap, and session snapshot cite the harmonized labels plus verification command. | Review ensures docs/tests updated same increment. | Drift between docs/tests/UI or missing verification. | No new telemetry. | Goals G-025-04.

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-025-01 | Keep dropdown copy concise enough to avoid truncation inside 320 px containers. | Console must stay accessible on smaller viewports. | Selenium/UI screenshots show no wrapping/truncation. | Operator UI CSS + Thymeleaf templates. | UI design guidelines.
| NFR-025-02 | Maintain deterministic preset ordering and counts across inline/stored dropdowns. | Operators rely on stable catalogue ordering. | Regression tests compare ordered lists per protocol. | Sample data helpers + Selenium harness. | Prior HOTP/TOTP/OCRA specs.
| NFR-025-03 | Ensure telemetry/logging redacts any sensitive seed material despite label changes. | TelemetryContracts guardrails. | Log inspection + ArchUnit telemetry checks. | application + rest-api modules. | Project constitution Principle 5.

## UI / Interaction Mock-ups (required for UI-facing work)
```
Before: [Load a sample vector]
└── Sample vector – SHA-1 6 digits (seeded demo)
└── Sample vector – SHA-1 8 digits (seeded demo)
└── Sample vector – Suite C, challenge/response

After: [Select a sample]
└── RFC 4226 – SHA-1, 6 digits
└── RFC 4226 – SHA-1, 8 digits
└── Demo suite – SHA-256, 6 digits
└── Demo suite – SHA-512, 8 digits
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-025-01 | HOTP/TOTP/OCRA/FIDO2 dropdowns adopt the concise label pattern with refreshed placeholders and Selenium coverage. |
| S-025-02 | HOTP seeded credential + inline preset catalogues stay aligned across SHA/digit variants. |
| S-025-03 | RFC 6287 suffixes applied to inline/stored OCRA presets while draft-only entries remain untouched. |
| S-025-04 | Documentation, roadmap, and verification logs reflect the rename with a recorded green `spotlessApply check`. |

## Test Strategy
- **Core:** No code impact; regressions focused on rest-api UI assets.
- **Application:** No changes; telemetry redaction verified via existing ArchUnit checks.
- **REST:** Update operator sample data helpers, Thymeleaf templates, and targeted unit tests covering preset lists.
- **CLI:** None.
- **UI (JS/Selenium):** Refresh Selenium tests for HOTP/TOTP/FIDO2 dropdowns and any inline placeholder assertions.
- **Docs/Contracts:** Update operator how-to guides, roadmap entry #21, and session snapshot with verification logs.

## Interface & Contract Catalogue
### Domain Objects
| ID | Description | Modules |
|----|-------------|---------|
| DO-025-01 | `OperatorPresetLabel` – tuple of `scenario`, `attributes`, and optional `annotation`. | rest-api (templates/JS) |
| DO-025-02 | `HotpSeededPresetDefinition` – seeded credential metadata mirrored between inline/stored dropdowns. | core, rest-api |

### API Routes / Services
| ID | Transport | Description | Notes |
|----|-----------|-------------|-------|
| API-025-01 | REST GET `/ui/console` | Serves the operator console with harmonized dropdown labels. | Thymeleaf templates pull from `*OperatorSampleData`. |
| API-025-02 | REST GET `/api/v1/ocra/credentials` | Ensures stored credential labels include RFC suffixes. | Label text only; payload schema unchanged. |

### CLI Commands / Flags
| ID | Command | Behaviour |
|----|---------|-----------|
| CLI-025-01 | — | No CLI-facing changes; commands reuse existing preset metadata. |

### Telemetry Events
| ID | Event name | Fields / Redaction rules |
|----|-----------|---------------------------|
| TE-025-01 | `operator.ui.sample.select` (existing) | Continue redacting seeded data; labels remain sanitized strings. |

### Fixtures & Sample Data
| ID | Path | Purpose |
|----|------|---------|
| FX-025-01 | `rest-api/src/main/java/io/openauth/sim/rest/ui/HotpOperatorSampleData.java` | Provides the HOTP preset catalogue shared by inline/stored dropdowns. |
| FX-025-02 | `rest-api/src/main/java/io/openauth/sim/rest/ui/TotpOperatorSampleData.java` | Supplies TOTP preset labels. |
| FX-025-03 | `rest-api/src/main/java/io/openauth/sim/rest/ui/OcraOperatorSampleData.java` | Houses RFC/draft preset labels plus suffix annotations. |
| FX-025-04 | `rest-api/src/main/java/io/openauth/sim/rest/ui/Fido2OperatorSampleData.java` | Defines attestation sample labels and placeholders. |

### UI States
| ID | State | Trigger / Expected outcome |
|----|-------|---------------------------|
| UI-025-01 | HOTP “Select a sample” dropdown | Displays six presets with concise labels and hints. |
| UI-025-02 | TOTP dropdown | Shows scenario/attribute labels without redundant wording. |
| UI-025-03 | OCRA stored credential dropdown | Appends `(RFC 6287)` only to RFC-backed entries. |
| UI-025-04 | FIDO2 inline/replay dropdowns | Render attestation presets with `<algorithm – UV state>` pattern. |

## Telemetry & Observability
- Telemetry events remain unchanged but must keep sanitized label strings. Spot-check operator console logs to confirm
  no raw seed data leaks after renaming.
- Continue emitting verbose trace entries exactly as before so downstream tooling only notices label text changes.

## Documentation Deliverables
- Update operator how-to guides and roadmap entry #21 with the concise label pattern.
- Capture migration progress plus verification logs in `docs/_current-session.md` and `docs/migration_plan.md`.

## Fixtures & Sample Data
No new fixtures required; reuse the updated preset helpers outlined in FX-025-01..04 and ensure seeded credential files
mirror the dropdown catalogue.

## Spec DSL
```
domain_objects:
  - id: DO-025-01
    name: OperatorPresetLabel
    fields:
      - name: scenario
        type: string
      - name: attributes
        type: string
      - name: annotation
        type: optional string
  - id: DO-025-02
    name: HotpSeededPresetDefinition
    fields:
      - name: algorithm
        type: enum[SHA-1,SHA-256,SHA-512]
      - name: digits
        type: enum[6,8]
      - name: credentialId
        type: string
routes:
  - id: API-025-01
    method: GET
    path: /ui/console
    description: Render operator console with harmonized labels
  - id: API-025-02
    method: GET
    path: /api/v1/ocra/credentials
    description: Surface stored credential labels with RFC suffixes
cli_commands:
  - id: CLI-025-01
    command: none
    description: CLI unaffected; preset catalogue reused
telemetry_events:
  - id: TE-025-01
    event: operator.ui.sample.select
    fields:
      - name: label
        type: sanitized string
fixtures:
  - id: FX-025-01
    path: rest-api/src/main/java/io/openauth/sim/rest/ui/HotpOperatorSampleData.java
    description: HOTP preset catalogue
ui_states:
  - id: UI-025-01
    description: HOTP dropdown showing concise labels
```

## Appendix (Optional)
- Selenium snapshots referencing these labels live under `rest-api/src/test/java/io/openauth/sim/rest/ui/`.
- Verification command: `./gradlew --no-daemon spotlessApply check` (2025-10-12) captured in session log.
EOF,workdir:.,max_output_tokens:6000}
