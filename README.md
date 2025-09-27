# OpenAuth Simulator

OpenAuth Simulator is a Java&nbsp;17, Gradle-based lab environment for emulating contemporary authentication credentials and protocols (FIDO2/WebAuthn, OATH/OCRA, EUDI wallet artefacts, EMV/CA, and future additions). The project is intentionally greenfield and non-production; we optimise for fast iteration by AI agents, incremental steps under ten minutes, and the ability to crush and rebuild APIs as requirements evolve.

## Current status (2025-09-27)

- ✅ Gradle multi-module skeleton (`core`, `cli`, `rest-api`, `ui`) backed by dependency locking and quality gates (Spotless, Checkstyle, SpotBugs, JaCoCo, OWASP Dependency Check).
- ✅ `core` module exposes the first programmatic APIs:
  - `Credential`, `SecretMaterial`, `CredentialType`, and supporting enums for secret encodings.
  - `CredentialStore` abstraction with a `MapDbCredentialStore` implementation that layers MapDB persistence with a Caffeine in-memory cache for hot reads.
  - JUnit 5 tests covering persistence, cache override behaviour, and in-memory variants.
- 🚧 Higher-level interfaces (`cli`, `rest-api`, `ui`) are placeholders; no user-facing flows yet.
- 🚧 Documentation scaffolding is in place under `docs/` and will capture architecture, ADRs, and operational playbooks as the emulator grows.

## Module map

| Module    | Purpose                                                          |
|-----------|------------------------------------------------------------------|
| `core`    | Pure Java API surface and credential persistence implementations |
| `cli`     | Placeholder for future Picocli-based tooling                     |
| `rest-api`| Placeholder for REST interface (planned Spring Boot service)     |
| `ui`      | Placeholder for server-rendered UI consuming the REST API        |

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

| Path                     | Contents (initial)                           |
|--------------------------|----------------------------------------------|
| `docs/0-overview`        | Product overview, glossary, scope            |
| `docs/1-concepts`        | Domain concepts, threat model (stub)         |
| `docs/2-how-to`          | Task guides (stub)                           |
| `docs/3-reference`       | Generated API references (stub)              |
| `docs/4-architecture`    | C4 diagrams, data flows (placeholder)        |
| `docs/5-operations`      | Runbooks and on-call docs (placeholder)      |
| `docs/6-decisions`       | ADRs; see ADR-0001 for persistence choice    |
| `docs/7-changelogs`      | Release notes / change log seeds             |
| `docs/8-compliance`      | Security & compliance posture (stub)         |
| `docs/_assets`           | Diagram sources and shared images            |

`agents.md` outlines expectations for AI agents managing the repository.

## Next steps

1. Flesh out the credential domain model (metadata, key material lifecycles, crypto primitives).
2. Instrument ArchUnit tests in `core` to keep packages honest as we expand.
3. Implement CLI ingestion flows (Picocli) wired to `CredentialStore`.
4. Stand up REST + UI modules (Spring Boot) with shared DTO contracts generated into `docs/3-reference`.
5. Add a JMeter plugin facade to provide a familiar load-testing UI against the emulator.

Contributions welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before raising PRs.
