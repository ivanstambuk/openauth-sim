# Feature 031 – Legacy Entry-Point Removal

_Status: Complete_  
_Last updated: 2025-10-21_

## Overview
Gradually accumulated compatibility paths keep older tooling and bookmarks working but now block simplifying the simulator. This feature retires the Java and JavaScript legacy entry points so only the canonical telemetry emitters, router state keys, and modern browser APIs remain. Operators and automated tests must migrate to the primary interfaces captured in Features 017, 024, 026, and 027.

## Goals
- Remove the CLI telemetry fallback (`legacyEmit`) so all events emit through the structured `TelemetryContracts` adapters.
- Delete the FIDO2 console `legacySetMode` bridge and the global `__openauth*` shims that retained pre-console routing behaviour.
- Drop acceptance of legacy query-parameter aliases across HOTP/TOTP/OCRA/FIDO2 views, keeping only the unified `protocol`, `tab`, and `mode` keys.
- Remove XMLHttpRequest fallbacks in the operator console so network calls rely on the standard Fetch API used by production browsers.
- Prune the WebAuthn “legacy” generator sample preset and align sample keys with the canonical W3C vectors.
- Update documentation, tests, and knowledge artefacts to reflect the absence of the above compatibility paths.

## Non-Goals
- Tightening REST or CLI request validation beyond the legacy entry points described here; payload defaults for optional fields remain unchanged.
- Removing the `openauth.sim.persistence.skip-upgrade` test-only flag (covered by Feature 012).
- Introducing new telemetry schemas or additional protocol routes.

## Clarifications
- 2025-10-19 – REST controller contracts stay as-is; we are not deprecating optional fields or relaxing JSON backwards compatibility.
- 2025-10-19 – Completion: T3107/T3108 aligned WebAuthn presets with W3C identifiers, refreshed documentation/knowledge artefacts, recorded the changelog entry, and re-ran the analysis gate to confirm a green baseline.

## Architecture & Design
- Replace the `legacyEmit` branch in `cli/OcraCli` with adapters-only emission and refresh tests that asserted the legacy text format.
- In the unified operator console scripts (`ui/ocra`, `ui/hotp`, `ui/totp`, `ui/fido2`), remove global shims and query-parameter coercion for legacy keys. Clamp state parsing to the canonical query params introduced in Feature 017.
- Ensure HtmlUnit-based Selenium harnesses enable the built-in `fetch` polyfill so production code can rely solely on the native Fetch API.
- Collapse the FIDO2 console API to expose only `setMode`, `setTab`, and ceremony helpers without dual pathways; broadcast events rely on the unified router state.
- Remove the `generator-es256` legacy sample from `WebAuthnGeneratorSamples`, align preset keys with canonical W3C fixture identifiers (for example `packed-es256`), and fall back to sanitized synthetic IDs when the specification omits private keys.
- Update knowledge artefacts (roadmap, knowledge map, how-to guides) and acceptance tests to reflect the removal of the legacy flows.

## Test Strategy
- Update CLI telemetry tests to assert the structured adapter output only.
- Refresh UI integration tests (Jest/HtmlUnit/Selenium) to drive navigation exclusively through canonical query params and ensure `fetch` is available in the test environment.
- Re-run the full UI Selenium suites alongside REST contract tests to confirm no regressions: `./gradlew --no-daemon :rest-api:test` (targeted FIDO2/OCRA/TOTP/HOTP suites) and `./gradlew spotlessApply check`.
- Maintain coverage gates by extending or deleting tests that referenced the removed compatibility helpers.

## Rollout & Migration
- Document the removal in operator-facing guides (`docs/2-how-to`) including the requirement to update bookmarks and automation scripts.
- Communicate telemetry format changes in the CLI reference before release.
- After verification, archive any deprecated examples into `docs/_archive/` if they remain useful historically.
