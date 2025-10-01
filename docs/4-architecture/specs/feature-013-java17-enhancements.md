# Feature 013 – Java 17 Language Enhancements

_Status: Complete_
_Last updated: 2025-10-01_

## Overview
Adopt additional Java 17 language features across the CLI and REST OCRA surfaces to tighten compile-time guarantees and simplify documentation. This feature seals the internal command hierarchy used by the CLI, reworks REST request normalization to rely on pattern matching over sealed request variants, and replaces verbose escaped JSON example strings with Java text blocks for maintainability. The work builds on Feature 011 by further modernising module internals without altering external contracts.

## Clarifications
1. 2025-10-01 – Scope is limited to the OCRA CLI and REST modules; core cryptography remains untouched during this feature (Option A).
2. 2025-10-01 – Sealed class adoption will cover nested command implementations within `OcraCli` only; Picocli wiring must remain functional without subclass changes (Option B).
3. 2025-10-01 – REST request normalization will expose sealed request variants consumed internally; REST controller contracts and payload schemas stay unchanged (Option A).
4. 2025-10-01 – Text-block migration targets the OpenAPI example payloads embedded in controller annotations; any additional escaped JSON found during implementation will be queued separately (Option A).
5. 2025-10-01 – Follow-up audit confirmed no additional CLI command hierarchies require sealing beyond `OcraCli`; future hierarchies should opt into the same pattern (Option A).
6. 2025-10-01 – Remaining REST controllers currently avoid escaped JSON payloads; new endpoints with inline examples must use text blocks by default (Option A).

## Functional Requirements
| ID | Requirement | Acceptance Signal |
|----|-------------|-------------------|
| J17-CLI-001 | Convert the `OcraCli` abstract command base to a sealed hierarchy that explicitly permits the existing subcommands. | `rg "sealed class AbstractOcraCommand"` returns the sealed definition; `./gradlew :cli:test` passes without reflective access or subclass regressions. |
| J17-REST-002 | Replace the REST OCRA evaluation and verification normalization logic with sealed request variants and pattern matching, eliminating nullable fields for mode discrimination. | Updated tests cover both stored and inline flows via the new sealed records; `./gradlew :rest-api:test --tests "*Ocra*ServiceTest"` passes. |
| J17-DOC-003 | Convert REST controller OpenAPI example strings to Java text blocks to improve readability while preserving output. | Controllers compile with text blocks; snapshot or serialization tests asserting example content continue to pass. |

## Non-Functional Requirements
| ID | Requirement | Target |
|----|-------------|--------|
| J17-NFR-001 | Maintain existing ArchUnit, PIT, and Jacoco thresholds after refactors. | `./gradlew qualityGate` remains green without threshold adjustments. |
| J17-NFR-002 | Avoid increasing build/runtime for REST normalization beyond the current baseline. | Service benchmarks or unit tests show no significant regression (>5%) compared to prior runs. |
| J17-NFR-003 | Keep sealed hierarchy definitions encapsulated within their modules (package-private or nested) to avoid expanding the public API surface. | Public API signatures remain unchanged; CLI continues to expose only the documented commands. |

## Test Strategy
- Extend existing CLI command tests to assert behaviour under the sealed hierarchy, ensuring no subclass leakage occurs.
- Update REST service tests to exercise both sealed request variants and verify exception handling paths using pattern matching.
- Ensure OpenAPI snapshot tests still pass after text block adoption.

## Dependencies & Risks
- Picocli may require default constructors or non-final classes; verify sealed modifiers do not interfere with framework instantiation.
- REST services must continue to satisfy Spring’s component scanning and serialization expectations after refactoring internals.
- Text block indentation must be preserved to avoid altering published example payloads; tests should detect regressions.

## Out of Scope
- Introducing sealed hierarchies or text blocks outside the CLI/REST modules identified above.
- Refactoring REST request DTOs or API contracts beyond internal normalization changes.
- Changing logging or telemetry formatting (handled previously in Feature 011/009).

## Verification
- `./gradlew :cli:test :rest-api:test` passes with the new sealed structures and text blocks.
- `./gradlew qualityGate` completes successfully with no new violations.
- OpenAPI snapshot tests verify that controller example payloads remain unchanged.
