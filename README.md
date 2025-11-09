# OpenAuth Simulator

OpenAuth Simulator is a Java&nbsp;17, Gradle-based lab environment for emulating contemporary authentication credentials and protocols (FIDO2/WebAuthn, OATH/OCRA, EUDI wallet artefacts, EMV/CAP, and future additions). The project is intentionally greenfield and non-production; we optimise for fast iteration by AI agents, incremental steps under ten minutes, and the ability to crush and rebuild APIs as requirements evolve.

## Current status (2025-09-30)

- ✅ `core` provides the OCRA credential domain, persistence adapters, and ArchUnit guards used by all facades.
- ✅ `cli` ships Picocli commands for importing, listing, deleting, evaluating credentials, and running MapDB maintenance tasks.
- ✅ `rest-api` exposes `/api/v1/ocra/evaluate` and `/api/v1/ocra/credentials`, publishes OpenAPI snapshots, and serves Swagger UI at `http://localhost:8080/swagger-ui/index.html` when booted locally.
- ✅ `ui` hosts the operator console at `/ui/console`, reusing the REST endpoints for inline and stored-credential evaluations.
- ✅ Documentation under `docs/` now covers operator workflows across Java integrations, CLI usage, REST operations, test vector generation, and persistence tuning.

## Module map

| Module    | Purpose                                                          |
|-----------|------------------------------------------------------------------|
| `core`    | OCRA credential domain, crypto helpers, persistence abstractions |
| `cli`     | Picocli tooling for credential lifecycle, evaluation, maintenance |
| `rest-api`| Spring Boot facade exposing OCRA evaluation and credential directory |
| `ui`      | Server-rendered operator console built atop the REST API         |

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

Use the shared init script at `tools/run-rest-api.init.gradle.kts` whenever you want to start the REST endpoints or operator console without IDE tooling. It registers two helper tasks:

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
1. Draft or update the feature specification (see `docs/4-architecture/specs/`).
2. Capture expected behaviour as failing tests or executable specifications.
3. Break the work into logical, self-contained tasks that are expected to complete within ≤90 minutes (shorter slices encouraged) and reference the spec plus staged tests.
4. Implement the smallest viable increment, keeping specs, plans, and docs in sync.

For more background, see the [GitHub Spec Kit guidance](https://github.com/github/spec-kit/blob/main/spec-driven.md) and the detailed agent workflow in `AGENTS.md`.

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

Consult the living [Implementation Roadmap](docs/4-architecture/roadmap.md) for future priorities, and see `AGENTS.md` for AI agent expectations. Contributions welcome—read [CONTRIBUTING.md](CONTRIBUTING.md) before raising PRs.

## Protocol Info embeddable assets

- CSS/JS bundles live at `rest-api/src/main/resources/static/ui/protocol-info.css` and `protocol-info.js`. They expose the `ProtocolInfo` API used by the operator console and external integrations.
- A standalone demo is available at `rest-api/src/main/resources/static/ui/protocol-info-demo.html` for manual QA without running the Spring Boot application.
- Integration guidance (including JSON schema, API usage, CustomEvents, and persistence keys) is documented in [docs/2-how-to/embed-protocol-info-surface.md](docs/2-how-to/embed-protocol-info-surface.md).
