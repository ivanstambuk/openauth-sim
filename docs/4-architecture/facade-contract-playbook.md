# Facade Contract Playbook

This document consolidates the cross-facade “contract” for OpenAuth Simulator: how protocol capabilities are exposed
consistently through the Native Java API, CLI, REST API, operator UI, MCP, and the standalone distribution.

It is intentionally **rule-focused**: protocol-specific behaviour belongs in the relevant feature specifications under
`docs/4-architecture/features/<NNN>/spec.md`. Governance and workflow policies live under Feature 011 and the project
constitution.

## Goals
- Provide a single reference for cross-facade conventions (naming, layering, persistence, telemetry, and output shape).
- Reduce duplication across feature specs by centralising facade invariants here.
- Keep the conventions enforceable via existing quality gates (ArchUnit + contract tests).

## Non-goals
- Introducing new protocol behaviours.
- Providing backward-compat shims for any facade (compatibility is opt-in and must be explicitly requested per scope).
- Replacing per-protocol specs, plans, or tasks.

## Global invariants (apply to every facade)

### Layering
- **`application/` is the facade seam.** Facades delegate protocol orchestration via `io.openauth.sim.application.*`
  services (see ADR-0007).
- **`core/` remains internal.** Facades avoid direct coupling to `io.openauth.sim.core.*` internals; enforcement lives in
  `core-architecture-tests` (e.g., `FacadeDelegationArchitectureTest`).

### Persistence
- Facades obtain stores via `io.openauth.sim.infra.persistence.CredentialStoreFactory` and depend on the
  `io.openauth.sim.core.store.CredentialStore` interface rather than MapDB builders (see Feature 012 + ArchUnit rules).

### Telemetry & verbose traces
- Facades emit operational events through `io.openauth.sim.application.telemetry.TelemetryContracts` adapters; avoid
  bespoke logging contracts (see `TelemetryContractArchitectureTest`).
- “Verbose trace” is a shared concept across facades: the user-facing toggles are per-facade, but trace payloads flow from
  the application layer and must remain sanitised.

### Contracts & schema governance
- **CLI JSON output:** any `--output-json` payload must validate against `docs/3-reference/cli/cli.schema.json` (ADR-0014).
- **REST:** OpenAPI snapshots (`docs/3-reference/rest-openapi.{json,yaml}`) are authoritative, and representative tests
  validate runtime response shapes against the `$ref` component schemas (Feature 013).
- **MCP:** tool catalogue and tool schemas are governed by Feature 015; MCP layers on top of REST per ADR-0010.

## Native Java seam (application)

### Entry-point rules
- Prefer the public “evaluation application services” as documented in `ReadMe.LLM` and Feature 014.
- Public request/response types used in documentation should live alongside the service as nested records (for example,
  `EvaluationCommand` / `EvaluationResult`) to keep discovery local.

### API hygiene rules
- No reflection (policy is enforced by `reflectionScan` and architecture tests).
- Javadoc must remain free of internal roadmap identifiers (see ADR-0008 and `NativeJavaJavadocPolicyTest`).

## CLI contract

### Naming and structure
- Commands are Picocli-based and live in `cli/` under `io.openauth.sim.cli.*`.
- Protocol commands delegate to application-layer services; “maintenance” commands are the only allowed exceptions for
  direct persistence interaction.

### Output and errors
- `--output-json` produces a stable envelope conforming to `docs/3-reference/cli/cli.schema.json` (ADR-0014).
- Exit codes remain consistent with Picocli conventions; validation failures are reported via structured `reasonCode`
  fields in JSON output where applicable (see ADR-0012/ADR-0014).

## REST contract

### URL and DTO conventions
- REST endpoints live under `/api/v1/**` and are implemented in `rest-api/` under `io.openauth.sim.rest.*`.
- Controllers/services delegate through application-layer services; REST DTOs are transport-layer adapters only.

### OpenAPI governance
- OpenAPI snapshots are enforced by tests; schema-required fields must reflect runtime truth (Feature 013).
- When changing request/response shapes, update the governing feature spec first, then the DTOs/tests, then regenerate the
  snapshot via `OpenApiSnapshotTest`.

## UI contract (operator console)

### Operator flow invariants
- UI lives under `ui/` (assets) and `rest-api` (Thymeleaf templates + controllers). UI changes are governed by Feature 009.
- Protocol panels reuse the shared console layout patterns (Evaluate/Replay, presets, and the global verbose-trace dock).
- UI does not introduce new simulator behaviour; it orchestrates existing REST endpoints.

## MCP contract

### Tool catalogue and helpers
- MCP is a first-class “agent facade” (Feature 015) backed by REST; it does not define parallel Java entry points.
- Tool catalogue (`tools/list`) must return JSON Schemas plus prompt hints and version metadata; helper tools may exist only
  in MCP if they are agent-specific (Feature 015).

### Telemetry and redaction
- MCP emits proxy/audit telemetry and returns redacted helper outputs; rate limiting remains upstream (ADR-0013).

## Standalone distribution contract

### Packaging rules
- The standalone fat JAR bundles simulator modules and documents/fixtures required for offline operation (ADR-0011).
- Standalone exposes the same CLI/REST/UI/MCP behaviours as the multi-module workspace; differences must be explicitly
  documented in the governing spec if they are ever introduced.

## Enforcement (how the playbook stays true)
- **Architecture rules:** `./gradlew --no-daemon :core-architecture-tests:test` (`@Tag("architecture")`).
- **Cross-facade parity:** `./gradlew --no-daemon :rest-api:test --tests "*CrossFacadeContractTest"` (`@Tag("crossFacadeContract")`).
- **Full verification:** `./gradlew --no-daemon spotlessApply check` (and `qualityGate` when required).

## When adding or changing a facade surface
- Update the governing feature spec first (behaviour + telemetry + contract shape).
- Add/extend executable coverage (ArchUnit, contract tests, or protocol tests) before implementation.
- Verify via the smallest possible Gradle targets, then finish with `./gradlew --no-daemon spotlessApply check`.

