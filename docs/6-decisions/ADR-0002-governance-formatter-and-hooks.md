# ADR-0002: Governance Formatter and Managed Hooks

- **Status:** Accepted
- **Date:** 2025-11-15
- **Related features/specs:** Feature 011 ([docs/4-architecture/features/011/spec.md](docs/4-architecture/features/011/spec.md))
- **Related open questions:** (none currently)

## Context

OpenAuth Simulator requires a deterministic, enforceable formatting and governance workflow so contributors can rely on a
single specification-driven process across local environments and CI. Historically, formatting guidance referenced
google-java-format and ad-hoc hooks, which caused drift between IDEs, Spotless, and CI. Governance for commit messages,
pre-commit checks, and formatter usage also lived in scattered docs, making it hard to reason about the effective policy.

Feature 011 consolidates governance under a single spec and must select:
- A canonical Java formatter and configuration (version, wrap width, Java level).
- The enforcement surface (managed Git hooks vs. CI-only checks).
- How these rules integrate with the constitution’s Specification-Driven Development flow (Principles 2 and 6).

## Decision

Adopt Palantir Java Format 2.78.0 (120-character wrap, Java 17) as the canonical formatter and enforce it via:
- Spotless configuration in [build.gradle.kts](build.gradle.kts) and the version catalog ([gradle/libs.versions.toml](gradle/libs.versions.toml)) pinned to Palantir 2.78.0.
- Managed Git hooks ([githooks/pre-commit](githooks/pre-commit), [githooks/commit-msg](githooks/commit-msg)) invoked through `core.hooksPath=githooks`:
  - [githooks/pre-commit](githooks/pre-commit) warms the Gradle configuration cache via `./gradlew --no-daemon help --configuration-cache`,
    retries `spotlessApply` once on stale-cache failures (deleting `.gradle/configuration-cache/**`), logs the retry outcome,
    and then runs Spotless, targeted Gradle tasks, and gitleaks.
  - [githooks/commit-msg](githooks/commit-msg) runs gitlint with the repository [.gitlint](.gitlint) profile, enforcing Conventional Commits with a
    100-character title and 120-character body lines.
- IDE formatter guidance aligned with Palantir’s configuration so local formatting matches Spotless output.

CI workflows mirror these policies by invoking `./gradlew --no-daemon spotlessApply check` and gitlint with the same config
on pushes/PRs. Feature 011’s spec/plan/tasks remain the single source of truth for these policies and reference this ADR.

## Consequences

### Positive
- Deterministic formatting across IDEs, local builds, and CI via a single Palantir/Spotless configuration.
- Clear governance workflow: contributors run managed hooks and `spotlessApply check` instead of guessing which checks apply.
- Conventional Commit enforcement via gitlint prevents ambiguous history and strengthens automation around release notes.
- Governance behaviour (hook guard, retries, formatter pin) is documented centrally in Feature 011 and the constitution.

### Negative
- Contributors must adopt Palantir Java Format in their IDEs; mixed formatter usage will cause noisy diffs until aligned.
- Pre-commit and commit-msg hooks introduce runtime overhead (up to the NFR budget) and may require tuning on slower machines.
- Changes to Palantir versions or formatter rules now require coordinated updates to Spotless, version locks, docs, and this ADR.

## Alternatives Considered

- **A – Continue with google-java-format and ad-hoc hooks**
  - Pros: Familiar tooling for some contributors; fewer initial changes.
  - Cons: Conflicts with current Spotless configuration; does not align with Feature 011’s Palantir adoption and would
    perpetuate formatter drift.
- **B – Palantir via CI-only checks (no managed hooks)**
  - Pros: Simpler local setup; avoids hook runtime overhead.
  - Cons: Defers failures to CI, slowing feedback loops; contradicts Feature 011’s goal of reproducible, pre-commit
    governance hooks and would weaken the hook guard pattern.
- **C – Palantir + managed hooks (chosen)**
  - Pros: Single canonical formatter with immediate local feedback and CI parity; integrates cleanly with SDD and drift gates.
  - Cons: Requires hook installation and occasional tuning of runtime budgets or retry behaviour.

## Security / Privacy Impact

- Formatter and hooks decisions do not change secret handling directly but formalise the use of gitleaks in pre-commit
  workflows, reducing the risk of committing credentials.
- Git hooks run locally and must avoid logging sensitive data; the current Spotless/gitlint/gitleaks pipeline uses paths
  and summaries only.

## Operational Impact

- Operators and maintainers must:
  - Keep `core.hooksPath` set to `githooks` and rerun the hook guard after clones or tooling resets.
  - Use `./gradlew --no-daemon spotlessApply check` and `./gradlew --no-daemon --write-locks spotlessApply check` when
    updating formatter locks.
  - Monitor pre-commit and commit-msg runtimes against NFR-011 budgets; adjust Gradle caching or hook sequencing if
    ergonomics degrade.
- CI pipelines must continue running gitlint and Spotless with Palantir configuration to stay aligned with local hooks.

## Links

- Related spec sections: `[docs/4-architecture/features/011/spec.md](docs/4-architecture/features/011/spec.md)#overview`, `#functional-requirements`,
  `#non-functional-requirements`, `#telemetry--observability`
- Related ADRs: ADR-0001 (Core Credential Store Stack)
- Related issues / PRs: (to be linked from future governance updates)

