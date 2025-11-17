# Feature 014 – Native Java API Facade Plan

| Field | Value |
|-------|-------|
| Status | Complete |
| Last updated | 2025-11-15 |
| Owners | Ivan (project owner) |
| Specification | [docs/4-architecture/features/014/spec.md](docs/4-architecture/features/014/spec.md) |
| Tasks checklist | [docs/4-architecture/features/014/tasks.md](docs/4-architecture/features/014/tasks.md) |
| Roadmap entry | #14 – Native Java API Facade |

## Vision & Success Criteria
Establish Native Java usage as a first-class facade across the simulator, governed by a shared pattern and ADR-0007, while
keeping per-protocol ownership intact. Success means:
- README, `docs/2-how-to`, and DeepWiki consistently describe the Native Java API as one of four consumption surfaces.
- OCRA’s existing Native Java API is documented as the reference pattern and remains in sync with this spec.
- Features 001, 002, 004, 005, and 006 each carry explicit backlog entries to expose or refine their Native Java APIs and
  publish at least one `*-from-java` guide.
- `./gradlew --no-daemon spotlessApply check` remains green after documentation and plan changes.

## Scope Alignment
- **In scope:**
  - Documenting the Native Java API pattern for per-protocol entry points.
  - Updating cross-cutting docs (README, `docs/2-how-to`, [.devin/wiki.json](.devin/wiki.json)) to treat Native Java as a facade.
  - Seeding backlog items in Features 001, 002, 004, 005, and 006 to implement or refine their Native Java APIs.
  - Planning future Javadoc generation and publication for facade entry points.
- **Out of scope:**
  - Implementing new runtime behaviour in HOTP/TOTP/FIDO2/EMV/EUDIW beyond what their specs already demand.
  - Shipping a consolidated cross-protocol Java SDK module; any such work requires a future feature.

## Dependencies & Interfaces
| Dependency | Notes |
|------------|-------|
| ADR-0007 – Native Java API Facade Strategy | Records the architectural decision to use per-protocol Native Java APIs. |
| Feature 003 (OCRA) | Provides the reference Native Java API pattern via `OcraCredentialFactory` + `OcraResponseCalculator` and `use-ocra-from-java.md`. |
| Features 001, 002, 004, 005, 006 | Will carry backlog entries referencing this plan/spec for protocol-specific Native Java APIs and guides. |
| Feature 010 (Documentation & Knowledge Automation) | Governs `docs/2-how-to`, README, roadmap, knowledge map, and DeepWiki alignment. |
| [.devin/wiki.json](.devin/wiki.json) | DeepWiki steering config that already lists a Native Java API page. |

## Assumptions & Risks
- **Assumptions:**
  - Protocol features are willing to expose a small, curated Java surface rather than relying on internal types.
  - Javadoc generation can be integrated into the Gradle build without major toolchain changes.
- **Risks:**
  - API surface creep if per-protocol seams grow without clear guidelines.
  - Drift between how-to docs and actual Java APIs if changes are not coordinated; mitigated by Feature 010 logging and this plan.

## Implementation Drift Gate
- **Drift Gate – 2025-11-15 (Native Java + Javadoc)**  
  - Summary: Verified that Native Java seams (HOTP, TOTP, WebAuthn, EMV/CAP, EUDIW) match this spec and their `*-from-java` guides, that usage tests cover happy-path and validation/error branches, and that Javadoc for `core`/`application` is enforced via `check`/`qualityGate` using `:application:nativeJavaApiJavadoc`. No high- or medium-impact drift was found; future Maven publishing and optional HTML hosting are deferred to later features referencing ADR-0008.

- **Checklist for future drift-gate runs (agents):**
  - **Preconditions**
    - [ ] Feature 014 spec/plan/tasks updated to current date and clearly describe:  
      - Native Java facade pattern (entry points, DTOs, error semantics, telemetry/Javadoc expectations).  
      - Javadoc aggregation approach via `:core:javadoc`, `:application:javadoc`, `:application:nativeJavaApiJavadoc`.  
    - [ ] [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md) has no `Open` rows for Feature 014.  
    - [ ] Latest commands have been run locally and logged in [docs/_current-session.md](docs/_current-session.md):  
      - `./gradlew --no-daemon :application:test --tests "*NativeJavaApiUsageTest"` (or individual per-protocol tests).  
      - `./gradlew --no-daemon :core-architecture-tests:test --tests "*NativeJavaJavadocPolicyTest"` (or equivalent guard).  
      - `./gradlew --no-daemon :application:nativeJavaApiJavadoc`.  
      - `./gradlew --no-daemon spotlessApply check`.  

  - **Seam inventory & contract check**  
    For each protocol seam (OCRA as reference, plus HOTP, TOTP, WebAuthn, EMV/CAP, EUDIW):  
    - [ ] Entry-point class and DTOs in `application` match the spec and protocol feature plan:  
      - Names and packages as documented in Feature 014 and the protocol spec/plan.  
      - Request/response types correspond to what the `*-from-java` guides describe.  
    - [ ] Javadoc on the seam class and key DTOs:  
      - Clearly label it as a “Native Java API seam” and cite ADR‑0007 / relevant public standards or how-to docs.  
      - Explicitly avoid internal roadmap identifiers (Feature numbers, FR/NFR IDs, task IDs).  
      - Point to the correct `docs/2-how-to/use-*-from-java.md` guide.  
    - [ ] `docs/2-how-to/use-*-from-java.md` examples:  
      - Use the same types and method names as the seam.  
      - Reflect the same success/failure branches as tests (e.g., validation failures, out-of-window checks, problem-details errors).  

  - **Tests & branch coverage**  
    - [ ] Each seam has at least one dedicated `*NativeJavaApiUsageTest` in `application` that covers:  
      - A “happy path” evaluation.  
      - At least one validation/failure scenario (e.g., missing counter, invalid assertion, out-of-window OTP, invalid presentation / Trusted Authorities miss).  
    - [ ] Usage tests are stable and green under:  
      - `./gradlew --no-daemon :application:test --tests "*NativeJavaApiUsageTest"`.  
    - [ ] Any missing branch coverage discovered during review is captured as explicit follow-up tasks (under the protocol feature and/or Feature 014).  

  - **Javadoc pipeline verification**  
    - [ ] `./gradlew --no-daemon :application:nativeJavaApiJavadoc` completes without errors (warnings acceptable if understood).  
    - [ ] `core/build/docs/javadoc` includes relevant helper types that `*-from-java` guides rely on (fixtures, evaluators, stores, credential factories).  
    - [ ] `application/build/docs/javadoc` includes all Native Java facade services and DTOs mentioned in guides.  
    - [ ] [docs/3-reference/native-java-api/README.md](docs/3-reference/native-java-api/README.md) correctly describes:  
      - The command (`./gradlew --no-daemon :application:nativeJavaApiJavadoc`).  
      - The output paths (`core/build/docs/javadoc`, `application/build/docs/javadoc`).  
      - The manual status of curated HTML under ``docs/3-reference/native-java-api`/`.  
    - [ ] [docs/3-reference/README.md](docs/3-reference/README.md) “Pending artifacts” entry matches the actual behaviour (command + paths).  

  - **Documentation sync & cross-feature alignment**  
    - [ ] README and [docs/2-how-to/README.md](docs/2-how-to/README.md) list Native Java as a first-class facade alongside CLI/REST/UI, in line with Feature 014 and ADR‑0007.  
    - [ ] Features 001/002/004/005/006 plans/tasks still contain the Native Java backlog references promised in this feature (or mark them completed where work has shipped).  
    - [ ] There is no drift between what Feature 014 says about Javadoc/CI and what Feature 010 plan/tasks + `docs/3-reference` now describe.  

  - **Drift capture & remediation**  
    - [ ] Any high-/medium-impact drift (e.g., guide names not matching classes, missing Javadoc references to ADR‑0007, broken tests) is:  
      - Logged in [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md) (Status: `Open`).  
      - Captured as follow-up tasks in the relevant feature plan/tasks.  
    - [ ] Low-impact issues (typos, minor Javadoc wording, small doc mismatches) are fixed directly as part of this increment, with:  
      - A brief note in this Implementation Drift Gate subsection.  
      - A short log entry in [docs/_current-session.md](docs/_current-session.md).  

  - **Gate output**  
    - [ ] This section is updated with the review date, commands run (with module focus and outcomes), per-seam findings (HOTP/TOTP/OCRA/WebAuthn/EMV/EUDIW), and any open questions/tasks spawned.  
    - [ ] [docs/_current-session.md](docs/_current-session.md) contains a concise recap under the Feature 014 section, including the Javadoc pipeline status and next steps (for example, future Maven publishing or curated HTML publishing when scoped).  

## Increment Map
1. **I1 – Pattern & documentation alignment (FR-014-01/02)**  
   - Document the Native Java API pattern in this spec (entry points, DTOs, error semantics, telemetry expectations).  
   - Align README and `docs/2-how-to` with four consumption surfaces; ensure DeepWiki steering ([.devin/wiki.json](.devin/wiki.json)) includes the Native Java API page.  
   - Commands: `./gradlew --no-daemon spotlessApply check`.

2. **I2 – Backlog seeding for existing protocols (FR-014-03)**  
   - Update Feature 001, 002, 004, 005, and 006 plans’ Follow-ups/Backlog sections with Native Java API increments referencing this spec/ADR-0007.  
   - For OCRA (Feature 003), record a short note tying its existing Native Java API to Feature 014/ADR-0007 for future refactors.  
   - Commands: `./gradlew --no-daemon spotlessApply check`.

3. **I3 – Javadoc & guide alignment planning (FR-014-02/04)**  
   - Capture Javadoc generation/publishing steps in this plan/tasks; ensure `docs/3-reference` has a placeholder for a Native Java API reference index under ``docs/3-reference/native-java-api`/`.  
   - Standardise on Gradle targets `:core:javadoc` and `:application:javadoc` for documentation of core helpers and Native Java entry points, and define an aggregation task (for example, `:application:nativeJavaApiJavadoc`) to be implemented under Feature 010 that exports a zipped bundle or curated pages into ``docs/3-reference/native-java-api`/`.  
   - Treat the CI wiring for Javadoc (when/where the aggregation task runs) as part of Feature 010’s documentation workflow, referenced from this plan but implemented in that feature’s plan/tasks.  
   - Commands for this increment: `./gradlew --no-daemon spotlessApply check` (design-only; no new Gradle tasks wired yet).

## Scenario Tracking
| Scenario | Description | Increment |
|----------|-------------|-----------|
| S-014-01 | Native Java API appears as a documented facade across README, `docs/2-how-to`, and DeepWiki. | I1 |
| S-014-02 | Features 001, 002, 004, 005, and 006 have explicit Native Java API backlog items referencing Feature 014. | I2 |

## Analysis Gate
- Run [docs/5-operations/analysis-gate-checklist.md](docs/5-operations/analysis-gate-checklist.md) once increments I1–I3 are in place or when the first protocol (beyond OCRA) is ready to implement its Native Java API.
- Ensure ADR-0007, this plan, Feature 010, and protocol feature plans all agree on responsibilities and surfaces.

## Exit Criteria
- Native Java API pattern documented and referenced by ADR-0007 and DeepWiki.
- Backlog entries for Native Java APIs and `*-from-java` guides exist in Features 001, 002, 004, 005, 006 (and OCRA’s mapping is documented).
- Javadoc generation/publishing approach captured in this plan/tasks (even if implementation is deferred).
