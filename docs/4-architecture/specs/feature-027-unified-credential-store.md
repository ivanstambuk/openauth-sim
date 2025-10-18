# Feature 027 – Unified Credential Store Naming

_Status: Proposed_  
_Last updated: 2025-10-18_

## Overview
Align all simulator facades on a single default MapDB credential store file so multi-protocol operators can share persisted credentials without manual path overrides. The change replaces legacy protocol-specific filenames (for example `ocra-credentials.db`) with an inclusive default (`credentials.db`) while retaining transparent fallbacks for existing deployments.

## Goals
- Standardise the default credential store filename across REST, CLI, and UI layers.
- Preserve backwards compatibility by auto-detecting legacy filenames during startup.
- Update documentation, tests, and knowledge artefacts to reflect the unified default and fallback behaviour.

## Non-Goals
- Introducing new persistence backends or modifying credential schemas.
- Migrating existing credential files in-place; legacy filenames remain valid via fallback resolution.
- Reworking encryption or cache settings for MapDB stores.

## Clarifications
- 2025-10-18 – Adopt the inclusive filename `credentials.db` as the shared default for all facades. (Owner directive.)
- 2025-10-18 – Change CLI defaults alongside REST/UI so every facade targets the shared file, with automatic fallback to legacy filenames during resolution. (Owner confirmed Option A.)

## Architecture & Design
- Update `CredentialStoreFactory.resolveDatabasePath` to prefer `credentials.db` while probing legacy filenames (`ocra-credentials.db`, `totp-credentials.db`, `hotp-credentials.db`, `fido2-credentials.db`) when the new default is absent and no explicit path is provided. Emit structured log telemetry when a legacy file is selected.
- Replace per-protocol default filename constants in CLI facades (HOTP, TOTP, OCRA, FIDO2) with the unified default and reuse a shared helper to describe the default path in help text.
- Update `RestPersistenceConfiguration` to reference the new default and rely on the factory fallback for legacy detection.
- Sync Selenium, MockMvc, and unit tests that assert the default filename towards `credentials.db`.
- Refresh documentation (how-to guides, knowledge map, roadmap entries) to describe the unified behaviour and explain the fallback window for legacy files.

## Test Strategy
- Extend `CredentialStoreFactoryTest` to cover fallback ordering (new default present, legacy file present, no file).
- Update REST configuration tests to assert the new default while verifying fallback detection through temporary files.
- Refresh CLI integration tests (where applicable) to confirm the resolved path matches the unified default.
- Execute the full regression set required by impacted modules: `./gradlew --no-daemon :infra-persistence:test`, `:cli:test`, `:rest-api:test`, and `spotlessApply check`.

## Rollout & Migration
- Document the change in `docs/2-how-to/configure-persistence-profiles.md`, highlighting that existing `*-credentials.db` files continue to load automatically.
- Note the unified filename in release notes and update the knowledge map so future workstream planning recognises the shared persistence surface.
- Keep the fallback probes in place for at least one major release and track eventual removal via the roadmap.
