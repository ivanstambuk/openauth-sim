# Feature 010 Tasks – Documentation & Knowledge Automation

_Status:_ Complete  
_Last updated:_ 2025-11-16

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).

## Checklist
- [x] T-010-05 – Add Native Java API Javadoc aggregation task (FR-014-02/04).  
  _Intent:_ Introduce `:application:nativeJavaApiJavadoc` as a documented aggregation task that runs `:core:javadoc` and `:application:javadoc` for Native Java entry points and produces a local artefact for operators; publishing into ``docs/3-reference/native-java-api`/` remains manual in this increment.  
  _Verification commands:_  
  - `./gradlew --no-daemon :application:nativeJavaApiJavadoc`  
  - `./gradlew --no-daemon spotlessApply check`  

- [x] T-010-06 – Introduce LLM/assistant-facing documentation surfaces (FR-010-13).  
  _Intent:_ Add [ReadMe.LLM](ReadMe.LLM) and [llms.txt](llms.txt) at the repository root, wire them into README/AGENTS/how-to guides, and ensure they describe canonical Native Java entry points for HOTP, TOTP, OCRA, FIDO2/WebAuthn, EMV/CAP, and EUDIW OpenID4VP.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`  
  _Notes:_ Treat this as the first Option B increment; future Option C work (for example MCP servers, JSON-LD metadata) will be coordinated with Feature 013 once these docs are stable.

- [x] T-010-07 – Wire Maven coordinates into docs and POM metadata (FR-010-01/02, FR-010-11, FR-010-13).  
  _Intent:_ Once public Maven coordinates are final, update [README.md](README.md), [ReadMe.LLM](ReadMe.LLM), and the relevant how-to guides with Maven/Gradle dependency snippets for each published artifact, and enrich the published POMs with descriptive `<name>`, `<description>`, `<url>`, `<licenses>`, `<scm>`, and `<developers>` fields that reflect the simulator’s protocols and non-production scope so AI assistants and search tooling can discover and consume the library reliably.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`  
  - `./gradlew --no-daemon :standalone:jar`  
  _Notes:_ Maven coordinates set to `io.github.ivanstambuk:openauth-sim-standalone`; README/ReadMe.LLM now outline the aggregated distribution module (no third-party libs bundled), credential requirements, Central Portal release sequence, `.github/workflows/publish-standalone.yml` (which writes `~/.gradle/gradle.properties` from secrets before publishing), and how consumers can exclude unwanted dependencies using [docs/3-reference/external-dependencies-by-facade-and-scenario.md](docs/3-reference/external-dependencies-by-facade-and-scenario.md).

- [x] T-010-08 – Apply the Option A documentation-link policy across all human-facing Markdown (NFR-010-06).  
  _Intent:_ Sweep README/AGENTS, `docs/0-overview`…8-compliance, feature specs/plans/tasks, runbooks, roadmap, knowledge map, `_current-session.md`, and any other human-readable Markdown to convert prose-level repo **file** references into `[path](path)` links, leave directories as inline code/plain text, and keep fenced code blocks untouched; record linting strategy and exceptions in `_current-session.md`.  
  _Verification commands:_  
  - `./gradlew --no-daemon spotlessApply check`  
  - `rg -n "docs/" `docs/2-how-to` [README.md](README.md) | grep -v '\['` (or equivalent) to confirm no remaining bare prose references.  
  _Notes:_ Executed via repository-wide Python sweeps (see [docs/_current-session.md](docs/_current-session.md)) plus manual regex spot-checks; Gradle verification deferred per user instruction—rerun when convenient. Capture any future automation (remark lint, custom script) in this feature before enforcing it in hooks/CI.

- [x] T-010-09 – Enforce Markdown newline guard in managed pre-commit hook (NFR-010-06, S-010-03).  
  _Intent:_ Add a repo-owned script that scans staged `.md` files for list items split by manual newlines (`,\n  and/or …`) and wire it into `githooks/pre-commit` so Option A formatting stays compliant without manual sweeps; document command usage in `_current-session.md`.  
  _Verification commands:_  
  - `tools/scripts/check-markdown-linewraps.py docs/2-how-to/use-hotp-from-java.md` (spot check – should exit 0).  
  - `./gradlew --no-daemon spotlessApply check`.  
  _Notes:_ Implemented `tools/scripts/check-markdown-linewraps.py`, added it to `githooks/pre-commit`, swept the repo via `tools/scripts/check-markdown-linewraps.py $(git ls-files '*.md')`, and added `_current-session.md` entries explaining the guard/Gradle verification. The checker only flags list entries whose next line begins with `and/or/command`, avoiding false positives for multi-paragraph bullets.

## Verification Log
- 2025-11-17 – `./gradlew --no-daemon :standalone:jar` and `./gradlew --no-daemon spotlessApply check` (PASS – verified the aggregated distribution module, publishing metadata, and README/ReadMe.LLM documentation updates for T-010-07).
- 2025-11-16 – `./gradlew --no-daemon spotlessApply check` (PASS – 22 s, 99 tasks: 6 executed, 93 up-to-date after introducing [ReadMe.LLM](ReadMe.LLM), [llms.txt](llms.txt), README/AGENTS/how-to cross-links for LLM/assistant usage; run logged here and in [docs/_current-session.md](docs/_current-session.md)).
- 2025-11-16 – `python - <<'PY' …` (PASS – converted 102 Markdown files to path links plus follow-up refinement passes; Gradle verification skipped per user request, see [docs/_current-session.md](docs/_current-session.md)).
- 2025-11-16 – `python - <<'PY' …` (PASS – refined sweep to keep directories unlinked and relink 115 Markdown files; Gradle verification skipped per user request, see [docs/_current-session.md](docs/_current-session.md)).
- 2025-11-15 – `./gradlew --no-daemon :application:nativeJavaApiJavadoc` and `./gradlew --no-daemon spotlessApply check` (Native Java Javadoc aggregation task T-010-05; runs `:core:javadoc` and `:application:javadoc`, updates `docs/3-reference` Native Java notes, and leaves publishing of curated HTML into ``docs/3-reference/native-java-api`/` as a follow-up step).
- 2025-11-13 – `./gradlew --no-daemon spotlessApply check` (documentation/automation drift gate verification run, 18 s, 96 tasks: 2 executed, 94 up-to-date).
- 2025-11-11 – `./gradlew --no-daemon spotlessApply check` (documentation/automation verification run, 24 s, configuration cache stored).
- 2025-11-11 – Documented required `./gradlew --no-daemon spotlessApply check` and `./gradlew --no-daemon qualityGate` commands to rerun after upcoming documentation increments land.

## Notes / TODOs
- Capture knowledge-map regeneration steps when automation scripting begins.
- Evaluate adding Markdown lint to the managed hook after the current verification backlog clears.
- When evolving the cross-cutting Native Java API feature (Feature 014), ensure its spec documents which Java entry points are considered stable public APIs, how Javadoc is generated/published (including the `:application:nativeJavaApiJavadoc` aggregation task), and how ``docs/2-how-to`/*-from-java.md` guides stay aligned with those contracts.
