# OpenAuth Simulator

OpenAuth Simulator is a Java&nbsp;17, Gradle-based lab environment for emulating contemporary authentication credentials and protocols (HOTP/TOTP, OATH OCRA, EMV/CAP, FIDO2/WebAuthn, and EUDIW OpenID4VP wallet artefacts). The project is intentionally greenfield and non-production; we optimise for fast iteration by AI agents, incremental steps under ten minutes, and the ability to crush and rebuild APIs as requirements evolve. The simulator can be consumed via four surfaces: a Native Java API, CLI commands, REST API endpoints, and an operator console web UI.

## What is this?

- Simulates OATH HOTP/TOTP and OCRA (RFC&nbsp;4226/6238/6287) using deterministic secrets and fixtures.
- Emulates EMV/CAP cardholder verification flows for lab and integration testing.
- Exercises FIDO2/WebAuthn assertions and EUDIW OpenID4VP wallet/verifier exchanges with synthetic PID artefacts.
- Provides four consumption surfaces:
  - Native Java API entry points (per protocol).
  - REST API (Spring Boot, OpenAPI-documented).
  - CLI (Picocli commands for credential lifecycle and evaluation).
  - Operator console UI for exploratory use.

**Not for:** production customer authentication, HSM-backed key management, or compliance-grade IAM systems.

## Current status (2025-11-16)

- ✅ `core` implements protocol primitives and fixtures for HOTP, TOTP, OCRA, FIDO2/WebAuthn, EMV/CAP, and EUDIW OpenID4VP.
- ✅ `application` exposes orchestration services and Native Java API seams (for example `HotpEvaluationApplicationService`, `TotpEvaluationApplicationService`, `OcraEvaluationApplicationService`, `EmvCapEvaluationApplicationService`, `WebAuthnEvaluationApplicationService`, `OpenId4VpWalletSimulationService`, `OpenId4VpValidationService`).
- ✅ `cli` ships Picocli commands for importing, listing, deleting, evaluating credentials, running MapDB maintenance tasks, and exercising fixtures across protocols.
- ✅ `rest-api` exposes JSON endpoints for the simulators, publishes OpenAPI snapshots, and serves Swagger UI at `http://localhost:8080/swagger-ui/index.html` when booted locally.
- ✅ `ui` hosts the operator console at /ui/console, reusing REST endpoints for inline and stored-credential evaluations.
- ✅ Documentation under `docs/` covers Java integrations, CLI usage, REST operations, Native Java usage from tools (JMeter/Neoload), test vector generation, and persistence tuning.

## Module map

| Module       | Purpose                                                                                       |
|--------------|-----------------------------------------------------------------------------------------------|
| `core`       | Protocol primitives (HOTP/TOTP/OCRA, FIDO2/WebAuthn, EMV/CAP, EUDIW helpers), crypto, fixtures |
| `application`| Orchestration services and Native Java API seams for all protocols                            |
| `cli`        | Picocli tooling for credential lifecycle, evaluation, maintenance, and simulator fixtures     |
| `rest-api`   | Spring Boot facade exposing JSON endpoints and Swagger/OpenAPI documentation                  |
| `ui`         | Server-rendered operator console built atop the REST API                                      |
| `infra-persistence` | MapDB-based `CredentialStoreFactory` and persistence defaults                          |
| `standalone` | Shadow-based distribution module that assembles the `openauth-sim-standalone` fat JAR for all facades |

## Quickstart by surface

### Native Java API (example: HOTP)

```java
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;

var store = CredentialStoreFactory.openInMemoryStore();
var service = new HotpEvaluationApplicationService(store);

EvaluationCommand.Inline cmd = new EvaluationCommand.Inline(
        "3132333435363738393031323334353637383930",
        HotpHashAlgorithm.SHA1,
        6,
        0L,
        Map.of(),
        0,
        0);

String otp = service.evaluate(cmd).otp();
```

See [docs/2-how-to/use-hotp-from-java.md](docs/2-how-to/use-hotp-from-java.md), [docs/2-how-to/use-totp-from-java.md](docs/2-how-to/use-totp-from-java.md), [docs/2-how-to/use-ocra-from-java.md](docs/2-how-to/use-ocra-from-java.md),
[docs/2-how-to/use-emv-cap-from-java.md](docs/2-how-to/use-emv-cap-from-java.md), [docs/2-how-to/use-fido2-from-java.md](docs/2-how-to/use-fido2-from-java.md), and [docs/2-how-to/use-eudiw-from-java.md](docs/2-how-to/use-eudiw-from-java.md)
for full Native Java examples across all protocols.

### CLI

```bash
./gradlew --no-daemon :cli:run --args="hotp evaluate --help"
```

Protocol-specific CLI usage (HOTP/TOTP/OCRA/FIDO2/EMV/EUDIW) is documented under ``docs/2-how-to`/*-cli-operations.md`.

### REST API and UI

```bash
./gradlew --no-daemon --init-script tools/run-rest-api.init.gradle.kts runRestApi
```

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Operator console: `http://localhost:8080/ui/console`

OpenAPI snapshots live under ``docs/3-reference`/` and are enforced by the `OpenApiSnapshotTest` suite.

### MCP proxy (agents)

1. Start the REST facade (see above) so `/api/v1/**` endpoints are available.
2. Create `~/.config/openauth-sim/mcp-config.yaml` (or pass `--config <path>`). Minimal example:

   ```yaml
   baseUrl: http://localhost:8080
   apiKey:
   timeouts:
     defaultMillis: 10000
     hotp.evaluate: 15000
   ```

3. Run the MCP proxy: `./gradlew --no-daemon :tools-mcp-server:run --args="--config ~/.config/openauth-sim/mcp-config.yaml"`.
4. Connect an MCP-aware client (for example `npx @modelcontextprotocol/cli`) to the spawned process. The server streams JSON-RPC messages over stdin/stdout using the standard `Content-Length` framing, exposing tools such as `hotp.evaluate`, `totp.evaluate`, `totp.helper.currentOtp`, `ocra.evaluate`, `emv.cap.evaluate`, `fido2.assertion.evaluate`, `eudiw.wallet.simulate`, `eudiw.presentation.validate`, and `fixtures.list`.

Each tool forwards the supplied JSON payload to the documented REST endpoint and returns the HTTP status/body to the MCP client, so assistants see precisely the same behaviour as human operators using the REST API or UI.

### JSON-LD metadata snapshots

Keep README/ReadMe.LLM structured data in sync by regenerating the JSON-LD snippets whenever simulator docs change. The generator now runs automatically inside `./gradlew check` and `./gradlew qualityGate`, and it skips rewriting files whose contents already match. Run it directly when you need to preview the updated snippets without the rest of the build:

```bash
./gradlew --no-daemon generateJsonLd
```

The task reads [docs/3-reference/json-ld/metadata.json](docs/3-reference/json-ld/metadata.json), refreshes snippet files under ``docs/3-reference/json-ld/snippets`/`, and writes a consolidated bundle to [build/json-ld/openauth-sim.json](build/json-ld/openauth-sim.json) for future hosted docs.

## Standalone distribution & Maven publishing

- The `:standalone` module builds **`io.github.ivanstambuk:openauth-sim-standalone`**, a fat JAR that bundles CLI, REST API, UI, and MCP proxy facades. Build it locally with:

  ```bash
  ./gradlew --no-daemon :standalone:shadowJar
  java -jar standalone/build/libs/openauth-sim-standalone-0.1.0-SNAPSHOT.jar --help
  ```

  (Replace the version suffix with the current `VERSION_NAME` when running locally.)

  (The manifest’s `Main-Class` points to the CLI launcher; REST and MCP facades remain available by running their entrypoints via `java -cp`.) Consumers who only need certain surfaces can remove the matching transitive dependencies using the coordinates catalogued in [docs/3-reference/external-dependencies-by-facade-and-scenario.md](docs/3-reference/external-dependencies-by-facade-and-scenario.md).

- Configure Maven publishing credentials and signing material **before** releasing:
  - PGP private key + passphrase exported to single-line properties `signingKey` / `signingPassword` (for example in `~/.gradle/gradle.properties`).
  - Central Portal credentials exposed via `mavenCentralPortalUsername` / `mavenCentralPortalPassword` (or the corresponding environment variables `MAVEN_CENTRAL_PORTAL_USERNAME` / `MAVEN_CENTRAL_PORTAL_PASSWORD`).
  - `GROUP` / `VERSION_NAME` properties already default to `io.github.ivanstambuk` and `0.1.0-SNAPSHOT`; override them per release as needed.
- GitHub Actions workflow [`.github/workflows/publish-standalone.yml`](.github/workflows/publish-standalone.yml) automates the release by writing `~/.gradle/gradle.properties` from the Central Portal + signing secrets and running the same Gradle tasks below.

- Release workflow (run from repo root once tests/quality gates are green):

  ```bash
  ./gradlew --no-daemon spotlessApply check
  ./gradlew --no-daemon :standalone:publishStandalonePublicationToProjectLocalRepository
  ./gradlew --no-daemon :standalone:zipMavenCentralPortalPublication
  ./gradlew --no-daemon :standalone:releaseMavenCentralPortalPublication
  ```

  `publishStandalonePublicationToMavenLocal` remains useful for smoke-testing dependency resolution before releasing. If Central validation fails, use `validateMavenCentralPortalPublication` and `dropMavenCentralPortalPublication` to inspect/abort the staged bundle, then rerun the release command.

These steps satisfy [docs/6-decisions/ADR-0011-standalone-fat-jar-distribution.md](docs/6-decisions/ADR-0011-standalone-fat-jar-distribution.md): one published artifact for operations simplicity, with per-facade dependency blacklists managed by consumers.

## Development quick start

```bash
# ensure JAVA_HOME points to your system's Java 17 installation
JAVA_HOME="${JAVA_HOME:?Set JAVA_HOME to a Java 17 JDK}" ./gradlew spotlessApply check
```

> On Ubuntu/WSL, install Java 17 via `sudo apt install openjdk-17-jdk` and set `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`. Use `sudo update-alternatives --config java` / `--config javac` to select the 17 toolchain.

Optional: point Git to the bundled hook to auto-run the command before every commit:

```bash
git config core.hooksPath githooks
```

The default build disables Error Prone for now because plugin 3.1.0 and recent Error Prone drops raise `--should-stop` incompatibilities. Re-enable once a compatible plugin is published by passing `-PerrorproneEnabled=true` and addressing any diagnostics during the run.

### Run the REST facade locally

Use the shared init script at [tools/run-rest-api.init.gradle.kts](tools/run-rest-api.init.gradle.kts) whenever you want to start the REST endpoints or operator console without IDE tooling. It registers two helper tasks:

- `runRestApi` launches `io.openauth.sim.rest.RestApiApplication` with the assembled runtime classpath.
- `printRestApiRuntimeClasspath` outputs the resolved classpath so you can invoke `java -cp …` manually if needed.

Typical usage from the repository root:

```bash
./gradlew --no-daemon --init-script tools/run-rest-api.init.gradle.kts runRestApi
```

Stop the service with `Ctrl+C`. To inspect the runtime classpath instead:

```bash
./gradlew --quiet --init-script tools/run-rest-api.init.gradle.kts printRestApiRuntimeClasspath
```

### Conventions

- All contributions happen in small, self-contained steps that are planned to take ≤90 minutes (execution may run longer if needed), each followed by `spotlessApply check` and a conventional commit.
- Tests must either pass or be explicitly disabled with a follow-up issue/ADR explaining the deferral.
- Secrets and credential material are injected through the API/CLI/REST layers (no static configuration files). Persistence is MapDB on-disk with in-memory caching; future adapters can be added behind `CredentialStore`.

### Specification-Driven Development (SDD)

The project runs on Specification-Driven Development: specifications lead every change, executable tests capture behaviour before code, and only then do we plan and implement tasks. The working rhythm is:
1. Draft or update the feature specification (stored at ``docs/4-architecture/features`/<NNN>/spec.md`).
2. Capture expected behaviour as failing tests or executable specifications.
3. Break the work into logical, self-contained tasks that are expected to complete within ≤90 minutes (shorter slices encouraged) and reference the spec plus staged tests.
4. Implement the smallest viable increment, keeping specs, plans, and docs in sync.

For more background, see the [GitHub Spec Kit guidance](https://github.com/github/spec-kit/blob/main/spec-driven.md) and the detailed agent workflow in [AGENTS.md](AGENTS.md).

## Documentation

Long-form documentation lives in `/docs`:

| Path                     | Highlights                                                   |
|--------------------------|--------------------------------------------------------------|
| `docs/0-overview`        | Product overview, glossary, scope                            |
| `docs/1-concepts`        | Domain concepts, capability matrix, telemetry references     |
| `docs/2-how-to`          | Operator guides for REST, CLI, Java integrations, UI usage   |
| `docs/3-reference`       | Generated artifacts including OpenAPI snapshots              |
| `docs/4-architecture`    | Specifications, feature plans, tasks, roadmap, knowledge map |
| `docs/5-operations`      | Runbooks and analysis gate checklist                         |
| `docs/6-decisions`       | ADRs, including the project constitution                     |
| `docs/7-changelogs`      | Release notes / change log seeds                             |
| `docs/8-compliance`      | Security & compliance posture (stub)                         |
| `docs/_assets`           | Diagram sources and shared images                            |

Consult the living [Implementation Roadmap](docs/4-architecture/roadmap.md) for future priorities, and see [AGENTS.md](AGENTS.md) for AI agent expectations. Contributions welcome—read [CONTRIBUTING.md](CONTRIBUTING.md) before raising PRs.

### For AI assistants and agents

- Use [ReadMe.LLM](ReadMe.LLM) for a compact, LLM-oriented overview of protocols, Native Java entry points, and minimal examples.
- Use [llms.txt](llms.txt) as the manifest of high-signal specs under `docs/4-architecture/features` when constructing context windows.
- Follow [AGENTS.md](AGENTS.md) for governance, workflow, and guardrails before making changes or suggesting refactors.

## Protocol Info embeddable assets

- CSS/JS bundles live at [rest-api/src/main/resources/static/ui/protocol-info.css](rest-api/src/main/resources/static/ui/protocol-info.css) and `protocol-info.js`. They expose the `ProtocolInfo` API used by the operator console and external integrations.
- A standalone demo is available at [rest-api/src/main/resources/static/ui/protocol-info-demo.html](rest-api/src/main/resources/static/ui/protocol-info-demo.html) for manual QA without running the Spring Boot application.
- Integration guidance (including JSON schema, API usage, CustomEvents, and persistence keys) is documented in [docs/2-how-to/embed-protocol-info-surface.md](docs/2-how-to/embed-protocol-info-surface.md).
