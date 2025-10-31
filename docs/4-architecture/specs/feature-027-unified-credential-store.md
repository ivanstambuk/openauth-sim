# Feature 027 – Unified Credential Store Naming

_Status:_ Complete  
_Last updated:_ 2025-10-31

## Overview
Align all simulator facades on a single default MapDB credential store file so multi-protocol operators can share persisted credentials without manual path overrides. The change replaces legacy protocol-specific filenames (for example `ocra-credentials.db`) with an inclusive default (`credentials.db`). Legacy filename detection is removed; future builds create or use the unified file exclusively unless an explicit path is supplied.

## Goals
- Standardise the default credential store filename across REST, CLI, and UI layers.
- Update documentation, tests, and knowledge artefacts to reflect the unified default and removal of fallback behaviour.

## Non-Goals
- Introducing new persistence backends or modifying credential schemas.
- Migrating existing credential files in-place; operators must manually rename or reconfigure legacy files if they wish to reuse them.
- Reworking encryption or cache settings for MapDB stores.

## Clarifications
- 2025-10-18 – Adopt the inclusive filename `credentials.db` as the shared default for all facades. (Owner directive.)
- 2025-10-18 – Change CLI defaults alongside REST/UI so every facade targets the shared file. (Owner confirmed Option A.)
- 2025-10-19 – Drop automatic detection of legacy filenames; simulator now requires either the unified default (`credentials.db`) or an explicit override. (Owner directive.)

## Architecture & Design
- Update `CredentialStoreFactory.resolveDatabasePath` to always return `credentials.db` when no explicit path is provided, without probing legacy filenames. Emit structured log telemetry only for explicit-path selections.
- Replace per-protocol default filename constants in CLI facades (HOTP, TOTP, OCRA, FIDO2) with the unified default and reuse a shared helper to describe the default path in help text.
- Update `RestPersistenceConfiguration` to reference the new default without additional legacy detection logic.
- Sync Selenium, MockMvc, and unit tests that assert the default filename towards `credentials.db`.
- Refresh documentation (how-to guides, knowledge map, roadmap entries) to describe the unified behaviour and clarify that operators must migrate legacy files manually.

## Test Strategy
- Update `CredentialStoreFactoryTest` to assert the unified default is always selected when no explicit path is supplied.
- Update REST configuration tests to assert the new default without legacy fallback scenarios.
- Refresh CLI integration tests (where applicable) to confirm the resolved path matches the unified default.
- Execute the full regression set required by impacted modules: `./gradlew --no-daemon :infra-persistence:test`, `:cli:test`, `:rest-api:test`, and `spotlessApply check`.

## Rollout & Migration
- Document the change in `docs/2-how-to/configure-persistence-profiles.md`, highlighting the need to rename or reconfigure legacy files manually.
- Note the unified filename in release notes and update the knowledge map so future workstream planning recognises the shared persistence surface.
- Remove the fallback probes from code and roadmap; track manual migration guidance only.

## Completion Notes
- 2025-10-19 – Persistence factory, CLI/REST defaults, and regression suites updated to enforce the unified `credentials.db` default with manual migration guidance; `./gradlew --no-daemon :infra-persistence:test :cli:test :rest-api:test spotlessApply check` executed successfully.
- 2025-10-29 – Documentation, roadmap, knowledge map, and session snapshot confirm the shared default, with no pending follow-ups.
