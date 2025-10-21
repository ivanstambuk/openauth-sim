# Feature 033 – Operator Console Naming Alignment

_Status: Complete_
_Last updated: 2025-10-21_

## Overview
The operator console hosted inside the `rest-api` module has grown from an OCRA-only surface to a unified control panel for HOTP, TOTP, and WebAuthn capabilities. Several Spring components, telemetry hooks, and documented references still carry legacy “Ocra” prefixes that no longer reflect the console’s scope. This feature aligns the terminology so future changes do not misinterpret the console as OCRA-specific.

## Goals
- Rename the primary MVC controller and related Spring beans to use neutral “OperatorConsole” naming.
- Update telemetry endpoints, loggers, and emitted event identifiers to remove the OCRA-only prefix while keeping payload semantics unchanged.
- Refactor unit, integration, and Selenium tests, along with Thymeleaf templates, so they reference the updated types and attributes.
- Synchronise documentation (session snapshot, knowledge map, relevant feature plans) with the refreshed naming.

## Non-Goals
- Introducing new functionality to the operator console.
- Modifying protocol-specific sample data classes that remain OCRA-only in purpose.
- Changing REST endpoint paths beyond the dedicated UI telemetry hook.

## Constraints
- Continue emitting telemetry through `TelemetryContracts` without introducing bespoke loggers.
- Preserve backwards compatibility for REST API endpoints already exposed to operators; only the internal UI telemetry hook may be renamed.
- Maintain adherence to the naming conventions enforced by Spotless/Palantir formatting.

## Clarifications
- 2025-10-21 – User selected Option B (“Comprehensive rename + telemetry alignment”), covering controller/bean renames, telemetry endpoint names, and related tests/templates. (Recorded in `docs/4-architecture/open-questions.md`.)

## Acceptance Criteria
1. All Spring MVC components, beans, and telemetry helpers referenced by the operator console use neutral `OperatorConsole*` naming.
2. The UI telemetry endpoint path matches the new naming, and Selenium/UI tests continue to pass.
3. Telemetry events emitted from the console report an updated event key (for example `event=ui.console.replay`) without altering existing metadata fields.
4. Documentation (knowledge map, session snapshot, referenced feature plans) reflects the new naming, and no references to `OcraOperatorUiController` remain outside OCRA-specific sample data classes.
5. `./gradlew spotlessApply check` completes successfully after the rename.
