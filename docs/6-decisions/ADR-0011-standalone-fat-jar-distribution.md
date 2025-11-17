# ADR-0011: OpenAuth Sim Standalone Fat JAR

- **Status:** Accepted
- **Date:** 2025-11-17
- **Related features/specs:** Feature 014 (docs/4-architecture/features/014/spec.md), Feature 015 (docs/4-architecture/features/015/spec.md)
- **Related open questions:** _None_

## Context

The simulator currently ships multiple Gradle subprojects (core, application, infra-persistence, cli, rest-api, ui, tools/mcp-server) and the documentation already enumerates facade-specific dependencies in [docs/3-reference/external-dependencies-by-facade-and-scenario.md](docs/3-reference/external-dependencies-by-facade-and-scenario.md). Operators consistently ask for a single downloadable artifact that includes every facade, while Native Java adopters (guided by how-to docs such as [docs/2-how-to/use-hotp-from-java.md](docs/2-how-to/use-hotp-from-java.md) and [docs/2-how-to/use-fido2-from-java.md](docs/2-how-to/use-fido2-from-java.md)) prefer to curate their own dependencies. Maintaining separate Maven artifacts for each module multiplies release effort and increases the risk of publishing inconsistent builds across facades.

## Decision

Publish a single "OpenAuth Sim Standalone" fat JAR (`io.github.ivanstambuk:openauth-sim-standalone`) that bundles cli, rest-api, ui, tools/mcp-server, and every supporting module. The Maven coordinates for this artifact become the official public distribution. Consumers who only need a subset of the simulator (for example, Native Java callers) rely on the dependency reference to exclude unneeded transitive dependencies from their own applications.

## Consequences

### Positive
- One installable artifact keeps CLI/REST/UI/MCP parity and shortens the release checklist.
- Operators can launch any facade from the same download without stitching submodules together.
- Documentation only needs to describe dependency exclusions rather than separate publishing flows per module.

### Negative
- Native Java consumers must maintain `<exclusions>` (or Gradle `exclude`) lists to keep their classpaths slim; missing an update may pull in unused frameworks.
- The fat JAR is significantly larger and carries every transitive dependency, increasing download time and storage footprint.
- Library consumers cannot rely on Maven to provide slim modules; they either depend on the monolith or repackage the sources locally.

## Alternatives Considered

- **Option A – Multi-artifact publishing (rejected):** Publish each module separately to Maven. Pros: consumers get precise dependencies; Cons: higher maintenance overhead, version drift risks, and more complex release notes.
- **Option B – Dual-track publishing (rejected):** Keep slim Maven artifacts plus a standalone fat JAR. Pros: best of both worlds; Cons: doubles validation surface and complicates support/documentation.
- **Option C – Fat JAR only (accepted):** Single artifact for all facades, rely on exclusion docs for consumers. Pros: simplest release pipeline; Cons: places exclusion burden on Native Java adopters.

## Security / Privacy Impact

Bundling every facade increases the attack surface in each runtime because unused libraries still ship inside the artifact. Dependency scanning and patching must treat the fat JAR as a whole; upgrading a vulnerable library means shipping a new all-in-one build immediately. On the positive side, operators cannot accidentally run outdated partial modules—the entire simulator updates in lockstep.

## Operational Impact

Release automation only needs to sign and upload one artifact, but verification must cover every facade before publishing the fat JAR. Documentation must stay synchronized so consumers know which exclusions are safe. Support workflows should reference the dependency matrix whenever someone strips dependencies from the fat JAR.

## Links

- Related spec sections: [docs/4-architecture/features/014/spec.md](docs/4-architecture/features/014/spec.md), [docs/4-architecture/features/015/spec.md](docs/4-architecture/features/015/spec.md)
- Dependency reference: [docs/3-reference/external-dependencies-by-facade-and-scenario.md](docs/3-reference/external-dependencies-by-facade-and-scenario.md)
- Native Java guides: [docs/2-how-to/use-hotp-from-java.md](docs/2-how-to/use-hotp-from-java.md), [docs/2-how-to/use-fido2-from-java.md](docs/2-how-to/use-fido2-from-java.md)
