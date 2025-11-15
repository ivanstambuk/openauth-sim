# Feature 010 Tasks – Documentation & Knowledge Automation

_Status:_ Complete  
_Last updated:_ 2025-11-15

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-`), non-goal IDs (`N-`), and scenario IDs (`S-<NNN>-`) inside the same parentheses immediately after the task title (omit categories that do not apply).

## Checklist
- [x] T-010-05 – Add Native Java API Javadoc aggregation task (FR-014-02/04).  
  _Intent:_ Introduce `:application:nativeJavaApiJavadoc` as a documented aggregation task that runs `:core:javadoc` and `:application:javadoc` for Native Java entry points and produces a local artefact for operators; publishing into `docs/3-reference/native-java-api/` remains manual in this increment.  
  _Verification commands:_  
  - `./gradlew --no-daemon :application:nativeJavaApiJavadoc`  
  - `./gradlew --no-daemon spotlessApply check`  

## Verification Log
- 2025-11-15 – `./gradlew --no-daemon :application:nativeJavaApiJavadoc` and `./gradlew --no-daemon spotlessApply check` (Native Java Javadoc aggregation task T-010-05; runs `:core:javadoc` and `:application:javadoc`, updates docs/3-reference Native Java notes, and leaves publishing of curated HTML into `docs/3-reference/native-java-api/` as a follow-up step).
- 2025-11-13 – `./gradlew --no-daemon spotlessApply check` (documentation/automation drift gate verification run, 18 s, 96 tasks: 2 executed, 94 up-to-date).
- 2025-11-11 – `./gradlew --no-daemon spotlessApply check` (documentation/automation verification run, 24 s, configuration cache stored).
- 2025-11-11 – Documented required `./gradlew --no-daemon spotlessApply check` and `./gradlew --no-daemon qualityGate` commands to rerun after upcoming documentation increments land.

## Notes / TODOs
- Capture knowledge-map regeneration steps when automation scripting begins.
- Evaluate adding Markdown lint to the managed hook after the current verification backlog clears.
- When evolving the cross-cutting Native Java API feature (Feature 014), ensure its spec documents which Java entry points are considered stable public APIs, how Javadoc is generated/published (including the `:application:nativeJavaApiJavadoc` aggregation task), and how `docs/2-how-to/*-from-java.md` guides stay aligned with those contracts.
