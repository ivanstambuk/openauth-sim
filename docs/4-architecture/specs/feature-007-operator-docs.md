# Feature 007 – Operator-Facing Documentation Suite

_Status: Draft_
_Last updated: 2025-09-30_

## Overview
Operators need a consolidated, human-friendly guide that explains how to work with the OpenAuth Simulator without reading internal architecture notes or code. This feature delivers a documentation suite that teaches operators how to drive the simulator through Java-based external applications, the command-line interface, and the REST API, while keeping the README aligned with the capabilities that ship today. The effort will restructure existing how-to content so operators can complete common OATH-OCRA tasks end-to-end: generate OTPs, replay assertions, and audit stored credentials.

## Clarifications
- 2025-09-30 – Java-focused documentation targets operators integrating external applications, demonstrating how to invoke simulator APIs for OTP/assertion generation and replay scenarios; it is not a developer API reference.
- 2025-09-30 – CLI coverage must describe every available command (import, list, delete, evaluate, maintenance) in a single operator-friendly guide stored under `docs/`.
- 2025-09-30 – REST coverage will be a brand-new human-friendly guide that incorporates and supersedes `docs/2-how-to/use-ocra-evaluation-endpoint.md`, documenting all REST endpoints and operations, not just evaluation.
- 2025-09-30 – README updates must reflect only the current feature set; scrub or relocate language about future/planned work when adding the Swagger UI link.
- 2025-09-30 – Java operator guide will showcase `OcraCredentialFactory` plus `OcraResponseCalculator` as the supported entry point; lower-level descriptor factories exist but remain out-of-scope for operators.
- 2025-09-30 – Java clients will manage credential descriptors in-memory keyed by name/ID or supply inline parameters; they must not interact with MapDB persistence directly.
- 2025-09-30 – Guides assume the OpenAuth Simulator JAR is already on the client classpath; build/distribution steps remain out of scope.

## Functional Requirements
1. Author an operator-focused how-to for Java integrations that:
   - Lives under `docs/2-how-to/`.
   - Explains prerequisites, simulator startup, and how external Java applications call into the simulator to generate or replay OTPs/assertions using provided utilities or REST/CLI bridges.
   - Provides runnable examples (Gradle snippets or sample classes) framed for operators integrating existing systems rather than core library developers.
2. Create a CLI operations guide under `docs/2-how-to/` that:
   - Documents every shipped OCRA CLI command (`import`, `list`, `delete`, `evaluate`, `maintenance compact`, `maintenance verify`).
   - Includes usage patterns, flag explanations, sample invocations, expected outputs, and troubleshooting cues tailored to operators.
   - Cross-references credential storage expectations (default MapDB path, secrets handling).
3. Produce a comprehensive REST operations guide that:
   - Replaces and subsumes the current `use-ocra-evaluation-endpoint.md` content.
   - Documents all REST endpoints exposed by the simulator (evaluation, credential directory, UI entry points where relevant) with request/response schemas, sample cURL/HTTPie snippets, error handling, and Swagger UI usage instructions.
   - Highlights the Swagger UI location (`http://localhost:8080/swagger-ui/index.html`) and explains how operators can regenerate the checked-in OpenAPI snapshots.
4. Update `README.md` so the "Current status" and usage sections describe only the capabilities that exist today (core domain, CLI, REST API, operator UI), add the Swagger UI link, and remove outdated placeholders about unimplemented interfaces.
5. Ensure all new or updated documents link to each other where appropriate and maintain consistency with existing terminology (e.g., OCRA suite naming, telemetry language).

## Non-Functional Requirements
- Documentation must use inclusive, operator-friendly tone and clearly separate prerequisites from execution steps.
- Provide copy-and-paste ready terminal commands and Java snippets that align with repository structure and default configurations.
- Verify Markdown builds cleanly (no broken links, no trailing spaces) and aligns with the project's style (80-100 char soft limit, fenced code blocks with language hints).
- Capture intent and references in the knowledge map and roadmap once documentation ships.

## Out of Scope
- Creating new simulator features or REST endpoints beyond what already exists.
- Automating documentation publishing or static site generation.
- Non-OCRA credential workflows; future workstreams will document those when available.

## Dependencies & Risks
- Requires up-to-date awareness of current CLI and REST capabilities; ensure docs stay synchronized with command/endpoint behaviour.
- README edits must avoid contradicting ongoing workstreams and maintainers' messaging.

## Verification
- Markdown lint and formatting checks pass implicitly through `./gradlew spotlessApply check`.
- Peer/self review confirms each guide enables an operator to complete OTP generation or replay tasks without consulting code.
- README hyperlinks (including Swagger UI) resolve when the simulator is running locally on the documented port.
