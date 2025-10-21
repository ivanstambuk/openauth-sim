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

### Conventions

- All contributions happen in small, self-contained steps (&lt;10 minutes), each followed by `spotlessApply check` and a conventional commit.
- Tests must either pass or be explicitly disabled with a follow-up issue/ADR explaining the deferral.
- Secrets and credential material are injected through the API/CLI/REST layers (no static configuration files). Persistence is MapDB on-disk with in-memory caching; future adapters can be added behind `CredentialStore`.

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
