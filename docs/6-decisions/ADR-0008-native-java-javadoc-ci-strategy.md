# ADR-0008: Native Java Javadoc CI Strategy (No Dedicated GitHub Workflow)

- **Status:** Accepted
- **Date:** 2025-11-15
- **Related features/specs:** Feature 010 (`docs/4-architecture/features/010/spec.md`), Feature 014 (`docs/4-architecture/features/014/spec.md`)
- **Related open questions:** none (discussion resolved inline during Feature 010/014 implementation)

## Context

Feature 010 (Documentation & Knowledge Automation) and Feature 014 (Native Java API Facade) require a repeatable way to
generate and validate Javadoc for the Native Java API seams. We introduced:

- `:core:javadoc` – standard Javadoc for the `core` module.
- `:application:javadoc` – standard Javadoc for the `application` module.
- `:application:nativeJavaApiJavadoc` – an aggregation task that depends on `:core:javadoc` and `:application:javadoc`
  and serves as the Native Java Javadoc entry point.

This aggregation task is now wired into the root Gradle `check` lifecycle:

- `tasks.named("check") { dependsOn(architectureTest, jacocoCoverageVerification, ":application:nativeJavaApiJavadoc") }`
- The managed quality gate (`qualityGate`) depends on `check`.
- GitHub CI (`.github/workflows/ci.yml`) invokes `./gradlew --no-daemon qualityGate`.
- The pre-commit hook runs `./gradlew --no-daemon spotlessApply check` as part of its pipeline.

As a result, any Javadoc failures (e.g., broken `@link` references, malformed tags, doclint errors if enabled) already
fail:

- Local pre-commit checks.
- The root `check` task.
- The GitHub CI quality gate.

During design, we considered adding a dedicated GitHub Actions workflow that would:

- Run `./gradlew --no-daemon :application:nativeJavaApiJavadoc` on CI.
- Upload the generated HTML as an artifact or publish it via GitHub Pages.

At the same time, we discussed future Maven publishing for the Native Java facade jars, including `-javadoc.jar`, which
would give IDE users access to Javadoc without any GitHub-hosted HTML site.

The question: **Do we need a separate GitHub Actions workflow specifically for Javadoc generation/publishing, or is the
existing Gradle/CI wiring sufficient for now?**

## Decision

We will **not** introduce a separate, dedicated GitHub Actions workflow solely for Native Java Javadoc generation or
HTML publication at this time. Instead, we will:

- Treat `:application:nativeJavaApiJavadoc` as the canonical Javadoc aggregation task for Native Java seams.
- Keep it wired into the root `check` lifecycle so:
  - The pre-commit hook and local `spotlessApply check` runs exercise `:core:javadoc` and `:application:javadoc`.
  - The GitHub CI quality gate (`./gradlew --no-daemon qualityGate`) exercises the same Javadoc tasks.
- Rely on future Maven publishing configuration to:
  - Produce a `-javadoc.jar` artifact for the Native Java facade.
  - Provide IDE-integrated Javadoc for consumers without requiring a GitHub-hosted HTML site.

If a future feature explicitly requires:

- A public HTML Javadoc site (e.g., via GitHub Pages), or
- Versioned Javadoc artifacts attached to GitHub releases,

then that feature will introduce a dedicated GitHub Actions workflow and reference this ADR as prior art. That workflow
will be scoped to publishing/hosting only, reusing the same underlying Gradle Javadoc tasks instead of duplicating logic.

## Consequences

### Positive

- **Single source of truth for Javadoc validation.** All environments (developer machines, pre-commit, CI) exercise the
  same Gradle Javadoc tasks via `check`/`qualityGate`, reducing drift.
- **No extra CI complexity.** We avoid an additional GitHub Actions workflow and separate configuration just for Javadoc
  generation, keeping the pipeline simpler and easier to maintain.
- **Aligned with Maven publishing.** When we later configure `maven-publish`, the same Javadoc tasks can be used to
  build `-javadoc.jar` artifacts without special GitHub logic.
- **Early error detection.** Because Javadoc now runs as part of `check`, broken `@link` references, malformed tags,
  or doclint problems are caught both locally (pre-commit) and in GitHub CI builds.

### Negative

- **No standalone Javadoc website yet.** We do not produce a public HTML Javadoc site (e.g., via GitHub Pages) as part
  of this decision. Developers either:
  - Open local Javadoc under `core/build/docs/javadoc` and `application/build/docs/javadoc`, or
  - Use IDE-integrated Javadoc from future `-javadoc.jar` artifacts.
- **No per-run Javadoc artifacts.** GitHub CI runs do not currently upload Javadoc HTML as build artifacts; if operators
  want downloadable HTML for a given run, a future workflow must add that.

## Alternatives Considered

- **A – Dedicated GitHub Actions Javadoc workflow (not chosen)**  
  - *Description:* Add a separate workflow (e.g., `native-java-javadoc.yml`) that runs `:application:nativeJavaApiJavadoc`
    on pushes/PRs and uploads the HTML as an artifact or publishes via GitHub Pages.  
  - *Pros:*  
    - Easy access to per-commit or per-release HTML documentation.  
    - Clear separation of responsibilities between quality gate and documentation publishing.  
  - *Cons:*  
    - Duplicates Javadoc execution already covered by `check`/`qualityGate`.  
    - Increases CI configuration complexity without immediate need.  
  - *Outcome:* Deferred; may be introduced by a future feature that explicitly requires hosted HTML docs.

- **B – Only local Javadoc, no CI integration (not chosen)**  
  - *Description:* Keep Javadoc generation as an ad-hoc local task (`:core:javadoc`, `:application:javadoc`) without
    wiring it into `check` or `qualityGate`.  
  - *Pros:*  
    - No additional CI runtime.  
  - *Cons:*  
    - Broken Javadoc (invalid links, malformed tags) would be easy to miss until much later.  
    - Inconsistent behaviour between developers who do or do not remember to run Javadoc locally.  
  - *Outcome:* Rejected; we explicitly want Javadoc issues to fail pre-commit and CI.

- **C – Integrate Javadoc into `check`/`qualityGate` only (chosen)**  
  - *Description:* Use `:application:nativeJavaApiJavadoc` as a dependency of `check`, and rely on `qualityGate` +
    pre-commit to run it in all important workflows.  
  - *Pros:*  
    - Single, predictable place where Javadoc is enforced.  
    - No extra workflows; CI behaviour matches local behaviour.  
  - *Cons:*  
    - Does not provide a hosted HTML site or artifacts by itself.  

## Security / Privacy Impact

- The Javadoc tasks operate only on source code already present in the repository; they do not introduce new secrets or
  external data flows.  
- Publishing `-javadoc.jar` via Maven in the future will expose API documentation that mirrors the open-source codebase
  and does not contain additional sensitive information beyond what is already public.  
- If a future workflow publishes HTML to GitHub Pages, it must continue to respect existing guidance on not embedding
  real credentials or secret test data in examples.

## Operational Impact

- **Pre-commit and CI failures.** Developers will see pre-commit and CI failures if:
  - Javadoc references are broken (`@link`, `@see`, `@throws`, `@param`, `@return` tags not matching signatures), or
  - Javadoc syntax/HTML is malformed enough to cause `:core:javadoc` or `:application:javadoc` to fail.  
  This is intentional and treated as part of the quality bar for Native Java seams.
- **No new secrets or configuration.** The decision does not require new CI secrets or environment variables; it reuses
  the existing `qualityGate` invocation (`./gradlew --no-daemon qualityGate`).
- **Future hosting decisions deferred.** Any decision to host Javadoc HTML on GitHub Pages or attach it to releases will
  be handled by a dedicated feature (and ADR) so that publishing responsibilities remain explicit and auditable.

## Links

- Related spec sections:
  - Feature 010 – Documentation & Knowledge Automation (`docs/4-architecture/features/010/spec.md`)
  - Feature 014 – Native Java API Facade (`docs/4-architecture/features/014/spec.md`)
- Related ADRs:
  - ADR-0004 – Documentation & Aggregated Quality Gate Workflow
  - ADR-0007 – Native Java API Facade Strategy
- Related CI configuration:
  - `.github/workflows/ci.yml` (`./gradlew --no-daemon qualityGate`)

