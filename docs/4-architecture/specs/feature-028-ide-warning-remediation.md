# Feature 028 – IDE Warning Remediation

_Status: In Progress_  
_Last updated: 2025-10-18_

## Overview
Resolve the outstanding IDE diagnostics captured on 2025-10-18 by tightening assertions and usages across application, core, CLI, REST, and UI test code. The goal is to eliminate dead assignments and unused locals without masking potential regressions, keeping the codebase warning-free while reinforcing test intent.

## Goals
- Replace placeholder locals and suppressions with meaningful assertions or telemetry checks so tests continue to describe expected behaviour.
- Remove redundant assignments in record canonical constructors while preserving optional semantics for evaluation timestamps.
- Maintain current functional behaviour for TOTP evaluation/replay, WebAuthn verification, CLI flows, and Selenium scenarios.
- Keep documentation artefacts (spec/plan/tasks/current session/roadmap) aligned with the remediation effort.

## Non-Goals
- Introducing new functional behaviour beyond the assertions required to justify existing variables.
- Expanding lint/static analysis tooling or modifying SpotBugs/Checkstyle configuration.
- Refactoring unrelated modules or consolidating Selenium flows.

## Clarifications
- 2025-10-18 – Adopt Option B: when addressing the flagged warnings, prefer strengthening assertions/usages over deleting placeholders so diagnostics continue to guard behaviour. (Owner decision.)
- 2025-10-19 – Adopt Option A: move `WebAuthnAssertionResponse` into its own source file so the DTO remains public while eliminating auxiliary-class compiler warnings in the REST module. (Owner decision.)
- 2025-10-19 – Adopt Option A: promote `spotbugs-annotations` to the application module’s exported compile classpath (`compileOnlyApi`) so downstream builds can resolve `@SuppressFBWarnings` without reintroducing SpotBugs violations. (Owner decision.)

## Architecture & Design
- **Application (TOTP evaluation/replay)** – Remove ineffective `evaluationInstant` assignments from record canonical constructors while keeping null allowance for on-demand instant resolution.
- **Core (FIDO2 attestation verifier)** – Use the decoded `clientData` to assert expected type/origin so the parsed structure contributes to verification.
- **Application/REST (WebAuthn replay service)** – Leverage the `metadata` local for follow-up assertions (for example, trust-anchor status) instead of leaving it unused.
- **CLI (TotpCliTest)** – Assert descriptor properties rather than retaining an unused variable, confirming the inline descriptor still matches expectations.
- **Tests (REST/Selenium)** – Convert unused locals/fields (`attestation`, `inlineSection`, `evaluateSelect`, `replaySelect`, `resultPanel`, etc.) into explicit assertions about element visibility or response payloads.
- **Testing utilities** – Remove obsolete `@SuppressWarnings("unchecked")` where generics are now inferred correctly.

## Test Strategy
- `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.totp.*"`
- `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationVerifierTest"`
- `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.TotpCliTest"`
- `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.*"`
- `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*"`
- `./gradlew --no-daemon spotlessApply check`

## Rollout & Regression
- Track remediation progress via the linked feature plan and tasks checklist; update `docs/_current-session.md` after each increment.
- Capture any new assertions or telemetry field checks in the relevant how-to guides if operator-facing behaviour changes.
- Monitor IDE diagnostics post-change to confirm the warning list is clear; document residual issues (if any) as follow-up tasks in the roadmap.
