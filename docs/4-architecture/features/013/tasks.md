# Feature 013 Tasks – Java 17 Language Enhancements

_Status: Complete_  
_Last updated: 2025-10-01_

## Checklist
- [x] T1301 – Add roadmap entry and baseline notes for Feature 013 (S13-01, S13-02, S13-03, S13-04).
  _Intent:_ Capture scope, constraints, and dependencies across CLI/REST/doc updates.
  _Verification commands:_
  - 26:| 10 | Java 17 language enhancements | Adopt sealed hierarchies and text blocks across OCRA CLI/REST internals | Complete | Spec: [Feature 013](features/013/spec.md), Plan: [Feature Plan 013](features/013/plan.md), Tasks: [Feature 013 Tasks](features/013/tasks.md); Closed 2025-10-01 with `qualityGate` validation |

- [x] T1302 – Document ≤90-minute increments in this checklist and confirm no open questions remain (S13-01, S13-02).
  _Intent:_ Keep the SDD artefacts synchronized before coding.
  _Verification commands:_
  - 

- [x] T1303 – Execute the analysis gate checklist and capture tooling readiness (S13-04).
  _Intent:_ Ensure preconditions (toolchain, gates) are verified before implementation.
  _Verification commands:_
  - # Analysis Gate Checklist

Use this checklist after a feature's specification, plan, and tasks exist but before implementation begins. After implementation, complete the Implementation Drift Gate section before the feature can be marked complete. Together these guardrails enforce the project constitution and keep specifications, plans, tasks, and code aligned.

## Inputs
- Feature specification (e.g., `docs/4-architecture/features/XXX/spec.md`)
- Feature plan (e.g., `docs/4-architecture/features/XXX/plan.md`)
- Feature tasks (e.g., `docs/4-architecture/tasks/feature-XXX-*.md`)
- Open questions log (`docs/4-architecture/open-questions.md`)
- Constitution (`docs/6-decisions/project-constitution.md`)
- Feature plan subsection reserved for the Implementation Drift Gate report (create if missing)

## Checklist
1. **Specification completeness** 
   - [ ] Objectives, functional, and non-functional requirements are populated.
   - [ ] Clarifications section reflects the latest answers for every high- and medium-impact question logged for this feature.
   - [ ] UI-impacting work includes an ASCII mock-up in the spec (`docs/4-architecture/spec-guidelines/ui-ascii-mockups.md`).
2. **Open questions review**
   - [ ] No blocking `Open` entries remain for this feature. If any exist, pause and obtain clarification.
3. **Plan alignment**
   - [ ] Feature plan references the correct specification and tasks files.
   - [ ] Dependencies and success criteria match the specification wording.
4. **Tasks coverage**
   - [ ] Every functional requirement maps to at least one task.
- [ ] Tasks sequence tests before implementation and keep planned increments ≤90 minutes by outlining logical, self-contained slices (execution may run longer if needed).
   - [ ] Planned tests enumerate the success, validation, and failure branches with failing cases queued before implementation begins.
5. **Constitution compliance**
   - [ ] No planned work violates principles (spec-first, clarification gate, test-first, documentation sync, dependency control).
   - [ ] Planned increments minimise new control-flow complexity by extracting validation/normalisation into small helpers, keeping each change nearly straight-line.
6. **Tooling readiness**
   - [ ] Commands (`./gradlew spotlessApply check`) documented for the feature plan or runbook.
   - [ ] SpotBugs dead-state detectors (Feature 015) noted, including the module command used to validate `URF/UWF/UUF/NP` findings.
   - [ ] Analysis results recorded in the feature plan (copy this checklist with pass/fail notes).

## Implementation Drift Gate (Pre-Completion)
Run this section once all planned tasks are complete and the latest build is green.

1. **Preconditions**
   - [ ] Feature tasks are all marked complete (☐ → ☑) and associated specs/plans reflect the final implementation.
   - [ ] Latest `./gradlew spotlessApply check` (or narrower documented suite) has passed within this increment.
2. **Cross-artifact validation**
   - [ ] Every high- and medium-impact specification requirement maps to executable code/tests; cite spec sections against classes/tests in the drift report, and note any low-level coverage adjustments.
   - [ ] No implementation or tests lack an originating spec/plan task; undocumented work is captured as a follow-up task or spec addition.
   - [ ] Feature plan and tasks remain consistent with the shipped implementation (dependencies, acceptance criteria, sequencing).
3. **Divergence handling**
   - [ ] High- and medium-impact gaps or over-deliveries are logged as new entries in `docs/4-architecture/open-questions.md` for user direction.
   - [ ] Low-impact or low-level drift (typos, minor wording, formatting) is corrected directly before finalising the report; document the fix without escalating.
   - [ ] Follow-up tasks or spec updates are drafted for any outstanding divergences awaiting approval.
4. **Coverage confirmation**
   - [ ] Tests exist for each success, validation, and failure branch enumerated in the specification, and their latest run is green.
   - [ ] Any missing coverage is documented with explicit tasks and blockers.
5. **Report & retrospective**
   - [ ] Implementation Drift Gate report added to the feature plan, detailing findings, artefact links, and reviewer(s).
   - [ ] Lessons learned and reusable guidance captured for future features (e.g., updates to specs/runbooks/templates).
   - [ ] Stakeholders (product, technical, AI agent as applicable) have acknowledged the report outcome before completion.

## Output
Document the outcome in the relevant feature plan under a "Analysis Gate" subsection, including:
- Date/time of the review
- Checklist pass/fail notes
- Follow-up actions or remediation tasks

Only proceed to implementation when every checkbox is satisfied or deferred with explicit owner approval.

For the Implementation Drift Gate, append the completed checklist and report summary to the feature plan. Do not mark the feature complete until all high/medium-impact divergences are resolved through updated specs, approved tasks, or user sign-off.

- [x] T1304 – Write failing CLI tests for the sealed command hierarchy, then implement the sealed classes (S13-01).
  _Intent:_ Enforce CLI command boundaries using Java 17 sealed types.
  _Verification commands:_
  - To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/9.2.0/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 
Reusing configuration cache.
> Task :core-ocra:processResources NO-SOURCE
> Task :core-shared:processResources NO-SOURCE
> Task :core:processTestFixturesResources NO-SOURCE
> Task :core:processResources NO-SOURCE
> Task :cli:processResources NO-SOURCE
> Task :infra-persistence:processResources NO-SOURCE
> Task :application:processResources NO-SOURCE
> Task :cli:processTestResources NO-SOURCE
> Task :core-shared:compileJava UP-TO-DATE
> Task :core-shared:classes UP-TO-DATE
> Task :core-shared:jar UP-TO-DATE
> Task :core:compileJava UP-TO-DATE
> Task :core:classes UP-TO-DATE
> Task :core:jar UP-TO-DATE
> Task :core:compileTestFixturesJava UP-TO-DATE
> Task :core:testFixturesClasses UP-TO-DATE
> Task :core-ocra:compileJava UP-TO-DATE
> Task :core-ocra:classes UP-TO-DATE
> Task :core:testFixturesJar UP-TO-DATE
> Task :core-ocra:jar UP-TO-DATE
> Task :infra-persistence:compileJava UP-TO-DATE
> Task :infra-persistence:classes UP-TO-DATE
> Task :infra-persistence:jar UP-TO-DATE
> Task :application:compileJava UP-TO-DATE
> Task :application:classes UP-TO-DATE
> Task :application:jar UP-TO-DATE
> Task :cli:compileJava UP-TO-DATE
> Task :cli:classes UP-TO-DATE
> Task :cli:compileTestJava UP-TO-DATE
> Task :cli:testClasses UP-TO-DATE
> Task :cli:test UP-TO-DATE

BUILD SUCCESSFUL in 16s
15 actionable tasks: 15 up-to-date
Configuration cache entry reused.

- [x] T1305 – Update REST evaluation service tests to use sealed request variants/pattern matching (S13-02).
  _Intent:_ Drive REST internals toward the new DTO/record structure.
  _Verification commands:_
  - To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/9.2.0/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 
Reusing configuration cache.
> Task :core-ocra:processResources NO-SOURCE
> Task :core:processResources NO-SOURCE
> Task :rest-api:processTestResources NO-SOURCE
> Task :infra-persistence:processResources NO-SOURCE
> Task :application:processResources NO-SOURCE
> Task :core-shared:processResources NO-SOURCE
> Task :rest-api:processResources UP-TO-DATE
> Task :core-shared:compileJava UP-TO-DATE
> Task :core-shared:classes UP-TO-DATE
> Task :core-shared:jar UP-TO-DATE
> Task :core:compileJava UP-TO-DATE
> Task :core:classes UP-TO-DATE
> Task :core:jar UP-TO-DATE
> Task :core-ocra:compileJava UP-TO-DATE
> Task :core-ocra:classes UP-TO-DATE
> Task :core-ocra:jar UP-TO-DATE
> Task :infra-persistence:compileJava UP-TO-DATE
> Task :infra-persistence:classes UP-TO-DATE
> Task :infra-persistence:jar UP-TO-DATE
> Task :application:compileJava UP-TO-DATE
> Task :application:classes UP-TO-DATE
> Task :application:jar UP-TO-DATE
> Task :rest-api:compileJava UP-TO-DATE
> Task :rest-api:classes UP-TO-DATE
> Task :rest-api:compileTestJava UP-TO-DATE
> Task :rest-api:testClasses UP-TO-DATE
> Task :rest-api:test FROM-CACHE

BUILD SUCCESSFUL in 15s
14 actionable tasks: 1 from cache, 13 up-to-date
Configuration cache entry reused.

- [x] T1306 – Update REST verification service tests to use sealed request variants (S13-02).
  _Intent:_ Cover the verification flow (stored + inline) under the new normalization logic.
  _Verification commands:_
  - To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/9.2.0/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 
Reusing configuration cache.
> Task :application:processResources NO-SOURCE
> Task :core-shared:processResources NO-SOURCE
> Task :core-ocra:processResources NO-SOURCE
> Task :rest-api:processTestResources NO-SOURCE
> Task :infra-persistence:processResources NO-SOURCE
> Task :core:processResources NO-SOURCE
> Task :rest-api:processResources UP-TO-DATE
> Task :core-shared:compileJava UP-TO-DATE
> Task :core-shared:classes UP-TO-DATE
> Task :core-shared:jar UP-TO-DATE
> Task :core:compileJava UP-TO-DATE
> Task :core:classes UP-TO-DATE
> Task :core:jar UP-TO-DATE
> Task :core-ocra:compileJava UP-TO-DATE
> Task :core-ocra:classes UP-TO-DATE
> Task :core-ocra:jar UP-TO-DATE
> Task :infra-persistence:compileJava UP-TO-DATE
> Task :infra-persistence:classes UP-TO-DATE
> Task :infra-persistence:jar UP-TO-DATE
> Task :application:compileJava UP-TO-DATE
> Task :application:classes UP-TO-DATE
> Task :application:jar UP-TO-DATE
> Task :rest-api:compileJava UP-TO-DATE
> Task :rest-api:classes UP-TO-DATE
> Task :rest-api:compileTestJava UP-TO-DATE
> Task :rest-api:testClasses UP-TO-DATE
> Task :rest-api:test

BUILD SUCCESSFUL in 24s
14 actionable tasks: 1 executed, 13 up-to-date
Configuration cache entry reused.

- [x] T1307 – Replace OpenAPI example strings with text blocks and validate via snapshot/unit tests (S13-03).
  _Intent:_ Improve readability while preserving published payloads.
  _Verification commands:_
  - To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/9.2.0/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 
Reusing configuration cache.
> Task :rest-api:processTestResources NO-SOURCE
> Task :core:processResources NO-SOURCE
> Task :core-shared:processResources NO-SOURCE
> Task :core-ocra:processResources NO-SOURCE
> Task :infra-persistence:processResources NO-SOURCE
> Task :application:processResources NO-SOURCE
> Task :rest-api:processResources UP-TO-DATE
> Task :core-shared:compileJava UP-TO-DATE
> Task :core-shared:classes UP-TO-DATE
> Task :core-shared:jar UP-TO-DATE
> Task :core:compileJava UP-TO-DATE
> Task :core:classes UP-TO-DATE
> Task :core:jar UP-TO-DATE
> Task :core-ocra:compileJava UP-TO-DATE
> Task :core-ocra:classes UP-TO-DATE
> Task :core-ocra:jar UP-TO-DATE
> Task :infra-persistence:compileJava UP-TO-DATE
> Task :infra-persistence:classes UP-TO-DATE
> Task :infra-persistence:jar UP-TO-DATE
> Task :application:compileJava UP-TO-DATE
> Task :application:classes UP-TO-DATE
> Task :application:jar UP-TO-DATE
> Task :rest-api:compileJava UP-TO-DATE
> Task :rest-api:classes UP-TO-DATE
> Task :rest-api:compileTestJava UP-TO-DATE
> Task :rest-api:testClasses UP-TO-DATE
> Task :rest-api:test

BUILD SUCCESSFUL in 58s
14 actionable tasks: 1 executed, 13 up-to-date
Configuration cache entry reused.

- [x] T1308 – Run Reusing configuration cache.
> Task :core-architecture-tests:processTestResources NO-SOURCE
> Task :infra-persistence:processResources NO-SOURCE
> Task :core-ocra:processResources NO-SOURCE
> Task :rest-api:processResources UP-TO-DATE
> Task :core-architecture-tests:compileJava NO-SOURCE
> Task :rest-api:processTestResources NO-SOURCE
> Task :application:processTestResources NO-SOURCE
> Task :core-shared:processResources NO-SOURCE
> Task :cli:processResources NO-SOURCE
> Task :core-shared:processTestResources NO-SOURCE
> Task :application:processResources NO-SOURCE
> Task :core:processTestResources NO-SOURCE
> Task :infra-persistence:processTestResources NO-SOURCE
> Task :core-architecture-tests:processResources NO-SOURCE
> Task :core:processTestFixturesResources NO-SOURCE
> Task :core-ocra:processTestResources NO-SOURCE
> Task :cli:processTestResources NO-SOURCE
> Task :ui:processResources NO-SOURCE
> Task :core:processResources NO-SOURCE
> Task :core-architecture-tests:classes UP-TO-DATE
> Task :spotlessInternalRegisterDependencies UP-TO-DATE
> Task :ui:processTestResources NO-SOURCE
> Task :core-shared:compileJava UP-TO-DATE
> Task :core-architecture-tests:spotbugsMain NO-SOURCE
> Task :core-architecture-tests:checkstyleMain NO-SOURCE
> Task :core-architecture-tests:pmdMain NO-SOURCE
> Task :core-shared:classes UP-TO-DATE
> Task :core-shared:checkstyleMain UP-TO-DATE
> Task :core-shared:compileTestJava UP-TO-DATE
> Task :core-shared:testClasses UP-TO-DATE
> Task :core:compileJava UP-TO-DATE
> Task :core-shared:jar UP-TO-DATE
> Task :core-shared:pmdMain UP-TO-DATE
> Task :core:classes UP-TO-DATE
> Task :core-shared:pmdTest UP-TO-DATE
> Task :core-shared:spotbugsTest UP-TO-DATE
> Task :core:jar UP-TO-DATE
> Task :core-shared:spotbugsMain UP-TO-DATE
> Task :core-shared:checkstyleTest UP-TO-DATE
> Task :core:checkstyleMain UP-TO-DATE
> Task :core-shared:test UP-TO-DATE
> Task :core:spotbugsMain UP-TO-DATE
> Task :core-shared:check UP-TO-DATE
> Task :core:pmdMain UP-TO-DATE
> Task :core:compileTestFixturesJava UP-TO-DATE
> Task :core:testFixturesClasses UP-TO-DATE
> Task :core-ocra:compileJava UP-TO-DATE
> Task :core-ocra:classes UP-TO-DATE
> Task :core:spotbugsTestFixtures UP-TO-DATE
> Task :core-ocra:jar UP-TO-DATE
> Task :core:testFixturesJar UP-TO-DATE
> Task :ui:compileJava NO-SOURCE
> Task :ui:classes UP-TO-DATE
> Task :infra-persistence:compileJava UP-TO-DATE
> Task :infra-persistence:classes UP-TO-DATE
> Task :ui:pmdMain NO-SOURCE
> Task :application:compileJava UP-TO-DATE
> Task :core:checkstyleTestFixtures UP-TO-DATE
> Task :application:classes UP-TO-DATE
> Task :ui:jar UP-TO-DATE
> Task :ui:checkstyleMain NO-SOURCE
> Task :core-ocra:spotbugsMain UP-TO-DATE
> Task :core-ocra:checkstyleMain UP-TO-DATE
> Task :ui:spotbugsMain NO-SOURCE
> Task :infra-persistence:jar UP-TO-DATE
> Task :core-ocra:pmdMain UP-TO-DATE
> Task :core:pmdTestFixtures UP-TO-DATE
> Task :core-ocra:compileTestJava UP-TO-DATE
> Task :ui:compileTestJava NO-SOURCE
> Task :infra-persistence:checkstyleMain UP-TO-DATE
> Task :core-ocra:testClasses UP-TO-DATE
> Task :ui:testClasses UP-TO-DATE
> Task :infra-persistence:pmdMain UP-TO-DATE
> Task :application:pmdMain UP-TO-DATE
> Task :core:compileTestJava UP-TO-DATE
> Task :core:testClasses UP-TO-DATE
> Task :ui:spotbugsTest NO-SOURCE
> Task :infra-persistence:spotbugsMain UP-TO-DATE
> Task :application:jar UP-TO-DATE
> Task :core-ocra:pmdTest UP-TO-DATE
> Task :ui:pmdTest NO-SOURCE
> Task :ui:checkstyleTest NO-SOURCE
> Task :core-ocra:spotbugsTest UP-TO-DATE
> Task :core-ocra:test UP-TO-DATE
> Task :application:checkstyleMain UP-TO-DATE
> Task :application:spotbugsMain UP-TO-DATE
> Task :core-ocra:checkstyleTest UP-TO-DATE
> Task :core-ocra:check UP-TO-DATE
> Task :ui:test NO-SOURCE
> Task :ui:check UP-TO-DATE
> Task :infra-persistence:compileTestJava UP-TO-DATE
> Task :infra-persistence:testClasses UP-TO-DATE
> Task :core:pmdTest UP-TO-DATE
> Task :core:spotbugsTest UP-TO-DATE
> Task :rest-api:compileJava UP-TO-DATE
> Task :core:test UP-TO-DATE
> Task :core:checkstyleTest UP-TO-DATE
> Task :rest-api:classes UP-TO-DATE
> Task :cli:compileJava UP-TO-DATE
> Task :cli:classes UP-TO-DATE
> Task :infra-persistence:spotbugsTest UP-TO-DATE
> Task :core:check UP-TO-DATE
> Task :infra-persistence:checkstyleTest UP-TO-DATE
> Task :infra-persistence:pmdTest UP-TO-DATE
> Task :rest-api:jar UP-TO-DATE
> Task :cli:jar UP-TO-DATE
> Task :cli:pmdMain UP-TO-DATE
> Task :cli:spotbugsMain UP-TO-DATE
> Task :rest-api:pmdMain UP-TO-DATE
> Task :cli:checkstyleMain UP-TO-DATE
> Task :rest-api:checkstyleMain UP-TO-DATE
> Task :infra-persistence:test UP-TO-DATE
> Task :infra-persistence:check UP-TO-DATE
> Task :cli:compileTestJava UP-TO-DATE
> Task :rest-api:spotbugsMain UP-TO-DATE
> Task :cli:testClasses UP-TO-DATE
> Task :cli:test UP-TO-DATE
> Task :cli:pmdTest UP-TO-DATE
> Task :cli:checkstyleTest UP-TO-DATE
> Task :cli:spotbugsTest UP-TO-DATE
> Task :cli:check UP-TO-DATE

> Task :rest-api:emvConsoleJsTest
TAP version 13
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey true
# [emv] toggleSensitiveFields emvCdol1 true
# [emv] toggleSensitiveFields emvIpb true
# [emv] toggleSensitiveFields emvIccTemplate true
# [emv] toggleSensitiveFields emvIssuerApplicationData true
# [emv] toggleSensitiveFields emvMasterKey true
# [emv] toggleSensitiveFields emvCdol1 true
# [emv] toggleSensitiveFields emvIpb true
# [emv] toggleSensitiveFields emvIccTemplate true
# [emv] toggleSensitiveFields emvIssuerApplicationData true
# [emv] toggleSensitiveFields emvMasterKey true
# [emv] toggleSensitiveFields emvCdol1 true
# [emv] toggleSensitiveFields emvIpb true
# [emv] toggleSensitiveFields emvIccTemplate true
# [emv] toggleSensitiveFields emvIssuerApplicationData true
# [emv] toggleSensitiveFields emvMasterKey true
# [emv] toggleSensitiveFields emvCdol1 true
# [emv] toggleSensitiveFields emvIpb true
# [emv] toggleSensitiveFields emvIccTemplate true
# [emv] toggleSensitiveFields emvIssuerApplicationData true
# [emv] toggleSensitiveFields emvMasterKey true
# [emv] toggleSensitiveFields emvCdol1 true
# [emv] toggleSensitiveFields emvIpb true
# [emv] toggleSensitiveFields emvIccTemplate true
# [emv] toggleSensitiveFields emvIssuerApplicationData true
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey true
# [emv] toggleSensitiveFields emvCdol1 true
# [emv] toggleSensitiveFields emvIpb true
# [emv] toggleSensitiveFields emvIccTemplate true
# [emv] toggleSensitiveFields emvIssuerApplicationData true
# [emv] toggleSensitiveFields emvMasterKey true
# [emv] toggleSensitiveFields emvCdol1 true
# [emv] toggleSensitiveFields emvIpb true
# [emv] toggleSensitiveFields emvIccTemplate true
# [emv] toggleSensitiveFields emvIssuerApplicationData true
# [emv] toggleSensitiveFields emvMasterKey true
# [emv] toggleSensitiveFields emvCdol1 true
# [emv] toggleSensitiveFields emvIpb true
# [emv] toggleSensitiveFields emvIccTemplate true
# [emv] toggleSensitiveFields emvIssuerApplicationData true
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvReplayMasterKey true
# [emv] toggleSensitiveFields emvReplayCdol1 true
# [emv] toggleSensitiveFields emvReplayIssuerBitmap true
# [emv] toggleSensitiveFields emvReplayIccTemplate true
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData true
# [emv] toggleSensitiveFields emvReplayMasterKey true
# [emv] toggleSensitiveFields emvReplayCdol1 true
# [emv] toggleSensitiveFields emvReplayIssuerBitmap true
# [emv] toggleSensitiveFields emvReplayIccTemplate true
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData true
# [emv] toggleSensitiveFields emvReplayMasterKey true
# [emv] toggleSensitiveFields emvReplayCdol1 true
# [emv] toggleSensitiveFields emvReplayIssuerBitmap true
# [emv] toggleSensitiveFields emvReplayIccTemplate true
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData true
# [emv] toggleSensitiveFields emvReplayMasterKey true
# [emv] toggleSensitiveFields emvReplayCdol1 true
# [emv] toggleSensitiveFields emvReplayIssuerBitmap true
# [emv] toggleSensitiveFields emvReplayIccTemplate true
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData true
# [emv] toggleSensitiveFields emvReplayMasterKey true
# [emv] toggleSensitiveFields emvReplayCdol1 true
# [emv] toggleSensitiveFields emvReplayIssuerBitmap true
# [emv] toggleSensitiveFields emvReplayIccTemplate true
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData true
# [emv] toggleSensitiveFields emvReplayMasterKey true
# [emv] toggleSensitiveFields emvReplayCdol1 true
# [emv] toggleSensitiveFields emvReplayIssuerBitmap true
# [emv] toggleSensitiveFields emvReplayIccTemplate true
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData true
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# Evaluate hydration debug: undefined
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvReplayMasterKey true
# [emv] toggleSensitiveFields emvReplayCdol1 true
# [emv] toggleSensitiveFields emvReplayIssuerBitmap true
# [emv] toggleSensitiveFields emvReplayIccTemplate true
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData true
# [emv] toggleSensitiveFields emvReplayMasterKey true
# [emv] toggleSensitiveFields emvReplayCdol1 true
# [emv] toggleSensitiveFields emvReplayIssuerBitmap true
# [emv] toggleSensitiveFields emvReplayIccTemplate true
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData true
# [emv] toggleSensitiveFields emvReplayMasterKey true
# [emv] toggleSensitiveFields emvReplayCdol1 true
# [emv] toggleSensitiveFields emvReplayIssuerBitmap true
# [emv] toggleSensitiveFields emvReplayIccTemplate true
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData true
# [emv] toggleSensitiveFields emvReplayMasterKey false
# [emv] toggleSensitiveFields emvReplayCdol1 false
# [emv] toggleSensitiveFields emvReplayIssuerBitmap false
# [emv] toggleSensitiveFields emvReplayIccTemplate false
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData false
# [emv] toggleSensitiveFields emvReplayMasterKey false
# [emv] toggleSensitiveFields emvReplayCdol1 false
# [emv] toggleSensitiveFields emvReplayIssuerBitmap false
# [emv] toggleSensitiveFields emvReplayIccTemplate false
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData false
# [emv] toggleSensitiveFields emvReplayMasterKey false
# [emv] toggleSensitiveFields emvReplayCdol1 false
# [emv] toggleSensitiveFields emvReplayIssuerBitmap false
# [emv] toggleSensitiveFields emvReplayIccTemplate false
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# Subtest: EMV evaluate action bar keeps stack spacing utility
ok 1 - EMV evaluate action bar keeps stack spacing utility
  ---
  duration_ms: 13.620267
  type: 'test'
  ...
# Subtest: EMV replay action bar reuses stack spacing utility
ok 2 - EMV replay action bar reuses stack spacing utility
  ---
  duration_ms: 1.840994
  type: 'test'
  ...
# Subtest: stored credential mode hides sensitive inputs and masks while leaving values blank
ok 3 - stored credential mode hides sensitive inputs and masks while leaving values blank
  ---
  duration_ms: 68.732516
  type: 'test'
  ...
# Subtest: session key derivation fields remain visible while stored mode hides sensitive evaluate inputs
ok 4 - session key derivation fields remain visible while stored mode hides sensitive evaluate inputs
  ---
  duration_ms: 16.25839
  type: 'test'
  ...
# Subtest: replay session key derivation fields remain visible when stored mode hides secrets
ok 5 - replay session key derivation fields remain visible when stored mode hides secrets
  ---
  duration_ms: 19.286814
  type: 'test'
  ...
# Subtest: card configuration fieldsets isolate transaction and customer sections
ok 6 - card configuration fieldsets isolate transaction and customer sections
  ---
  duration_ms: 3.591505
  type: 'test'
  ...
# Subtest: customer input grid exposes a single shared set of inputs for evaluate mode
ok 7 - customer input grid exposes a single shared set of inputs for evaluate mode
  ---
  duration_ms: 1.352126
  type: 'test'
  ...
# Subtest: customer input grid exposes a single shared set of inputs for replay mode
ok 8 - customer input grid exposes a single shared set of inputs for replay mode
  ---
  duration_ms: 0.82242
  type: 'test'
  ...
# Subtest: selecting a preset while inline mode is active keeps inline controls editable
ok 9 - selecting a preset while inline mode is active keeps inline controls editable
  ---
  duration_ms: 10.123913
  type: 'test'
  ...
# Subtest: inline preset hydration populates sensitive fields for evaluate flow
ok 10 - inline preset hydration populates sensitive fields for evaluate flow
  ---
  duration_ms: 24.961312
  type: 'test'
  ...
# Subtest: inline preset hydration populates sensitive fields for replay flow
ok 11 - inline preset hydration populates sensitive fields for replay flow
  ---
  duration_ms: 6.578801
  type: 'test'
  ...
# Subtest: CAP mode toggles customer inputs
ok 12 - CAP mode toggles customer inputs
  ---
  duration_ms: 15.844991
  type: 'test'
  ...
# Subtest: inline submit with preset falls back to stored credential when secrets are blank
ok 13 - inline submit with preset falls back to stored credential when secrets are blank
  ---
  duration_ms: 12.78671
  type: 'test'
  ...
# Subtest: inline preset submit preserves overrides while keeping credential fallback
ok 14 - inline preset submit preserves overrides while keeping credential fallback
  ---
  duration_ms: 10.211958
  type: 'test'
  ...
# Subtest: inline verbose trace forwards provenance sections to the console
ok 15 - inline verbose trace forwards provenance sections to the console
  ---
  duration_ms: 5.594621
  type: 'test'
  ...
# Subtest: stored submission posts credential ID to stored endpoint when verbose enabled
ok 16 - stored submission posts credential ID to stored endpoint when verbose enabled
  ---
  duration_ms: 12.152611
  type: 'test'
  ...
# Subtest: overriding stored fields triggers inline fallback with inline payload
ok 17 - overriding stored fields triggers inline fallback with inline payload
  ---
  duration_ms: 5.141392
  type: 'test'
  ...
# Subtest: stored submission honours verbose toggle when disabled
ok 18 - stored submission honours verbose toggle when disabled
  ---
  duration_ms: 18.00094
  type: 'test'
  ...
1..18
# tests 18
# suites 0
# pass 18
# fail 0
# cancelled 0
# skipped 0
# todo 0
# duration_ms 586.155922

> Task :spotlessJava UP-TO-DATE
> Task :core-architecture-tests:compileTestJava UP-TO-DATE
> Task :application:compileTestJava UP-TO-DATE
> Task :core-architecture-tests:testClasses UP-TO-DATE
> Task :application:testClasses UP-TO-DATE
> Task :spotlessJavaApply UP-TO-DATE
> Task :spotlessJavaCheck UP-TO-DATE
> Task :core-architecture-tests:pmdTest UP-TO-DATE
> Task :core-architecture-tests:checkstyleTest UP-TO-DATE
> Task :application:pmdTest UP-TO-DATE
> Task :application:checkstyleTest UP-TO-DATE
> Task :core-architecture-tests:spotbugsTest UP-TO-DATE
> Task :application:test UP-TO-DATE
> Task :core-architecture-tests:test UP-TO-DATE
> Task :architectureTest UP-TO-DATE
> Task :core-architecture-tests:check UP-TO-DATE
> Task :application:spotbugsTest UP-TO-DATE
> Task :application:check UP-TO-DATE
> Task :rest-api:compileTestJava UP-TO-DATE
> Task :rest-api:testClasses UP-TO-DATE
> Task :rest-api:checkstyleTest UP-TO-DATE
> Task :rest-api:pmdTest UP-TO-DATE
> Task :rest-api:spotbugsTest UP-TO-DATE
> Task :spotlessMisc UP-TO-DATE
> Task :spotlessKotlinGradle UP-TO-DATE
> Task :spotlessMiscApply UP-TO-DATE
> Task :spotlessMiscCheck UP-TO-DATE
> Task :spotlessKotlinGradleApply UP-TO-DATE
> Task :spotlessKotlinGradleCheck UP-TO-DATE
> Task :spotlessCheck UP-TO-DATE
> Task :spotlessApply UP-TO-DATE

> Task :rest-api:eudiwConsoleJsTest
TAP version 13
# Subtest: EUDIW console renders multi-presentation sections with trace IDs and actions
ok 1 - EUDIW console renders multi-presentation sections with trace IDs and actions
  ---
  duration_ms: 47.137548
  type: 'test'
  ...
# Subtest: Deep-link parameters hydrate tab and mode state
ok 2 - Deep-link parameters hydrate tab and mode state
  ---
  duration_ms: 2.97904
  type: 'test'
  ...
# Subtest: syncDeepLink pushes alias URLs into history
ok 3 - syncDeepLink pushes alias URLs into history
  ---
  duration_ms: 3.318301
  type: 'test'
  ...
# Subtest: syncDeepLink preserves canonical protocol when no alias is provided
ok 4 - syncDeepLink preserves canonical protocol when no alias is provided
  ---
  duration_ms: 3.12772
  type: 'test'
  ...
# Subtest: initial URL search hydrates state without pushing history
ok 5 - initial URL search hydrates state without pushing history
  ---
  duration_ms: 2.695022
  type: 'test'
  ...
# Subtest: history state fallback tolerates partial state objects
ok 6 - history state fallback tolerates partial state objects
  ---
  duration_ms: 2.768194
  type: 'test'
  ...
# Subtest: protocol activation reuses the initial search state before rewrites
ok 7 - protocol activation reuses the initial search state before rewrites
  ---
  duration_ms: 11.889465
  type: 'test'
  ...
# Subtest: popstate restores prior alias, tab, and mode selections
ok 8 - popstate restores prior alias, tab, and mode selections
  ---
  duration_ms: 6.194815
  type: 'test'
  ...
1..8
# tests 8
# suites 0
# pass 8
# fail 0
# cancelled 0
# skipped 0
# todo 0
# duration_ms 545.569849

> Task :rest-api:test UP-TO-DATE
> Task :rest-api:check
> Task :jacocoAggregatedReport UP-TO-DATE
> Task :jacocoCoverageVerification UP-TO-DATE
> Task :check UP-TO-DATE

BUILD SUCCESSFUL in 10s
96 actionable tasks: 2 executed, 94 up-to-date
Configuration cache entry reused. (plus  as bandwidth allows) and capture outcomes (S13-04).
  _Intent:_ Confirm the refactor keeps the pipeline green.
  _Verification commands:_
  - To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/9.2.0/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 
Reusing configuration cache.
> Task :ui:processResources NO-SOURCE
> Task :core-architecture-tests:compileJava NO-SOURCE
> Task :core-architecture-tests:processResources NO-SOURCE
> Task :core:processTestResources NO-SOURCE
> Task :rest-api:processTestResources NO-SOURCE
> Task :infra-persistence:processResources NO-SOURCE
> Task :core-architecture-tests:processTestResources NO-SOURCE
> Task :core-shared:processTestResources NO-SOURCE
> Task :application:processResources NO-SOURCE
> Task :infra-persistence:processTestResources NO-SOURCE
> Task :core:processResources NO-SOURCE
> Task :core-shared:processResources NO-SOURCE
> Task :core-ocra:processTestResources NO-SOURCE
> Task :core:processTestFixturesResources NO-SOURCE
> Task :cli:processResources NO-SOURCE
> Task :cli:processTestResources NO-SOURCE
> Task :application:processTestResources NO-SOURCE
> Task :core-ocra:processResources NO-SOURCE
> Task :core-architecture-tests:classes UP-TO-DATE
> Task :ui:processTestResources NO-SOURCE
> Task :core-architecture-tests:spotbugsMain NO-SOURCE
> Task :core-architecture-tests:pmdMain NO-SOURCE
> Task :core-architecture-tests:checkstyleMain NO-SOURCE
> Task :spotlessInternalRegisterDependencies UP-TO-DATE
> Task :rest-api:processResources UP-TO-DATE
> Task :core-shared:compileJava UP-TO-DATE
> Task :core-shared:classes UP-TO-DATE
> Task :core-shared:jar UP-TO-DATE
> Task :core-shared:compileTestJava UP-TO-DATE
> Task :core-shared:testClasses UP-TO-DATE
> Task :core-shared:spotbugsMain UP-TO-DATE

> Task :rest-api:emvConsoleJsTest
TAP version 13
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey true
# [emv] toggleSensitiveFields emvCdol1 true
# [emv] toggleSensitiveFields emvIpb true
# [emv] toggleSensitiveFields emvIccTemplate true
# [emv] toggleSensitiveFields emvIssuerApplicationData true
# [emv] toggleSensitiveFields emvMasterKey true
# [emv] toggleSensitiveFields emvCdol1 true
# [emv] toggleSensitiveFields emvIpb true
# [emv] toggleSensitiveFields emvIccTemplate true
# [emv] toggleSensitiveFields emvIssuerApplicationData true
# [emv] toggleSensitiveFields emvMasterKey true
# [emv] toggleSensitiveFields emvCdol1 true
# [emv] toggleSensitiveFields emvIpb true
# [emv] toggleSensitiveFields emvIccTemplate true
# [emv] toggleSensitiveFields emvIssuerApplicationData true
# [emv] toggleSensitiveFields emvMasterKey true
# [emv] toggleSensitiveFields emvCdol1 true
# [emv] toggleSensitiveFields emvIpb true
# [emv] toggleSensitiveFields emvIccTemplate true
# [emv] toggleSensitiveFields emvIssuerApplicationData true
# [emv] toggleSensitiveFields emvMasterKey true
# [emv] toggleSensitiveFields emvCdol1 true
# [emv] toggleSensitiveFields emvIpb true
# [emv] toggleSensitiveFields emvIccTemplate true
# [emv] toggleSensitiveFields emvIssuerApplicationData true
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey true
# [emv] toggleSensitiveFields emvCdol1 true
# [emv] toggleSensitiveFields emvIpb true
# [emv] toggleSensitiveFields emvIccTemplate true
# [emv] toggleSensitiveFields emvIssuerApplicationData true
# [emv] toggleSensitiveFields emvMasterKey true
# [emv] toggleSensitiveFields emvCdol1 true
# [emv] toggleSensitiveFields emvIpb true
# [emv] toggleSensitiveFields emvIccTemplate true
# [emv] toggleSensitiveFields emvIssuerApplicationData true
# [emv] toggleSensitiveFields emvMasterKey true
# [emv] toggleSensitiveFields emvCdol1 true
# [emv] toggleSensitiveFields emvIpb true
# [emv] toggleSensitiveFields emvIccTemplate true
# [emv] toggleSensitiveFields emvIssuerApplicationData true
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvReplayMasterKey true
# [emv] toggleSensitiveFields emvReplayCdol1 true
# [emv] toggleSensitiveFields emvReplayIssuerBitmap true
# [emv] toggleSensitiveFields emvReplayIccTemplate true
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData true
# [emv] toggleSensitiveFields emvReplayMasterKey true
# [emv] toggleSensitiveFields emvReplayCdol1 true
# [emv] toggleSensitiveFields emvReplayIssuerBitmap true
# [emv] toggleSensitiveFields emvReplayIccTemplate true
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData true
# [emv] toggleSensitiveFields emvReplayMasterKey true
# [emv] toggleSensitiveFields emvReplayCdol1 true
# [emv] toggleSensitiveFields emvReplayIssuerBitmap true
# [emv] toggleSensitiveFields emvReplayIccTemplate true
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData true
# [emv] toggleSensitiveFields emvReplayMasterKey true
# [emv] toggleSensitiveFields emvReplayCdol1 true
# [emv] toggleSensitiveFields emvReplayIssuerBitmap true
# [emv] toggleSensitiveFields emvReplayIccTemplate true
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData true
# [emv] toggleSensitiveFields emvReplayMasterKey true
# [emv] toggleSensitiveFields emvReplayCdol1 true
# [emv] toggleSensitiveFields emvReplayIssuerBitmap true
# [emv] toggleSensitiveFields emvReplayIccTemplate true
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData true
# [emv] toggleSensitiveFields emvReplayMasterKey true
# [emv] toggleSensitiveFields emvReplayCdol1 true
# [emv] toggleSensitiveFields emvReplayIssuerBitmap true
# [emv] toggleSensitiveFields emvReplayIccTemplate true
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData true
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# Evaluate hydration debug: undefined
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvReplayMasterKey true
# [emv] toggleSensitiveFields emvReplayCdol1 true
# [emv] toggleSensitiveFields emvReplayIssuerBitmap true
# [emv] toggleSensitiveFields emvReplayIccTemplate true
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData true
# [emv] toggleSensitiveFields emvReplayMasterKey true
# [emv] toggleSensitiveFields emvReplayCdol1 true
# [emv] toggleSensitiveFields emvReplayIssuerBitmap true
# [emv] toggleSensitiveFields emvReplayIccTemplate true
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData true
# [emv] toggleSensitiveFields emvReplayMasterKey true
# [emv] toggleSensitiveFields emvReplayCdol1 true
# [emv] toggleSensitiveFields emvReplayIssuerBitmap true
# [emv] toggleSensitiveFields emvReplayIccTemplate true
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData true
# [emv] toggleSensitiveFields emvReplayMasterKey false
# [emv] toggleSensitiveFields emvReplayCdol1 false
# [emv] toggleSensitiveFields emvReplayIssuerBitmap false
# [emv] toggleSensitiveFields emvReplayIccTemplate false
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData false
# [emv] toggleSensitiveFields emvReplayMasterKey false
# [emv] toggleSensitiveFields emvReplayCdol1 false
# [emv] toggleSensitiveFields emvReplayIssuerBitmap false
# [emv] toggleSensitiveFields emvReplayIccTemplate false
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData false
# [emv] toggleSensitiveFields emvReplayMasterKey false
# [emv] toggleSensitiveFields emvReplayCdol1 false
# [emv] toggleSensitiveFields emvReplayIssuerBitmap false
# [emv] toggleSensitiveFields emvReplayIccTemplate false
# [emv] toggleSensitiveFields emvReplayIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# [emv] toggleSensitiveFields emvMasterKey false
# [emv] toggleSensitiveFields emvCdol1 false
# [emv] toggleSensitiveFields emvIpb false
# [emv] toggleSensitiveFields emvIccTemplate false
# [emv] toggleSensitiveFields emvIssuerApplicationData false
# Subtest: EMV evaluate action bar keeps stack spacing utility
ok 1 - EMV evaluate action bar keeps stack spacing utility
  ---
  duration_ms: 4.942033
  type: 'test'
  ...
# Subtest: EMV replay action bar reuses stack spacing utility
ok 2 - EMV replay action bar reuses stack spacing utility
  ---
  duration_ms: 0.985258
  type: 'test'
  ...
# Subtest: stored credential mode hides sensitive inputs and masks while leaving values blank
ok 3 - stored credential mode hides sensitive inputs and masks while leaving values blank
  ---
  duration_ms: 62.744714
  type: 'test'
  ...
# Subtest: session key derivation fields remain visible while stored mode hides sensitive evaluate inputs
ok 4 - session key derivation fields remain visible while stored mode hides sensitive evaluate inputs
  ---
  duration_ms: 14.772923
  type: 'test'
  ...
# Subtest: replay session key derivation fields remain visible when stored mode hides secrets
ok 5 - replay session key derivation fields remain visible when stored mode hides secrets
  ---
  duration_ms: 19.357445
  type: 'test'
  ...
# Subtest: card configuration fieldsets isolate transaction and customer sections
ok 6 - card configuration fieldsets isolate transaction and customer sections
  ---
  duration_ms: 0.494326
  type: 'test'
  ...
# Subtest: customer input grid exposes a single shared set of inputs for evaluate mode
ok 7 - customer input grid exposes a single shared set of inputs for evaluate mode
  ---
  duration_ms: 0.576931
  type: 'test'
  ...
# Subtest: customer input grid exposes a single shared set of inputs for replay mode
ok 8 - customer input grid exposes a single shared set of inputs for replay mode
  ---
  duration_ms: 0.360224
  type: 'test'
  ...
# Subtest: selecting a preset while inline mode is active keeps inline controls editable
ok 9 - selecting a preset while inline mode is active keeps inline controls editable
  ---
  duration_ms: 13.70402
  type: 'test'
  ...
# Subtest: inline preset hydration populates sensitive fields for evaluate flow
ok 10 - inline preset hydration populates sensitive fields for evaluate flow
  ---
  duration_ms: 20.65588
  type: 'test'
  ...
# Subtest: inline preset hydration populates sensitive fields for replay flow
ok 11 - inline preset hydration populates sensitive fields for replay flow
  ---
  duration_ms: 10.851814
  type: 'test'
  ...
# Subtest: CAP mode toggles customer inputs
ok 12 - CAP mode toggles customer inputs
  ---
  duration_ms: 3.654102
  type: 'test'
  ...
# Subtest: inline submit with preset falls back to stored credential when secrets are blank
ok 13 - inline submit with preset falls back to stored credential when secrets are blank
  ---
  duration_ms: 11.151343
  type: 'test'
  ...
# Subtest: inline preset submit preserves overrides while keeping credential fallback
ok 14 - inline preset submit preserves overrides while keeping credential fallback
  ---
  duration_ms: 16.422915
  type: 'test'
  ...
# Subtest: inline verbose trace forwards provenance sections to the console
ok 15 - inline verbose trace forwards provenance sections to the console
  ---
  duration_ms: 9.301335
  type: 'test'
  ...
# Subtest: stored submission posts credential ID to stored endpoint when verbose enabled
ok 16 - stored submission posts credential ID to stored endpoint when verbose enabled
  ---
  duration_ms: 11.546506
  type: 'test'
  ...
# Subtest: overriding stored fields triggers inline fallback with inline payload
ok 17 - overriding stored fields triggers inline fallback with inline payload
  ---
  duration_ms: 3.791171
  type: 'test'
  ...
# Subtest: stored submission honours verbose toggle when disabled
ok 18 - stored submission honours verbose toggle when disabled
  ---
  duration_ms: 12.928373
  type: 'test'
  ...
1..18
# tests 18
# suites 0
# pass 18
# fail 0
# cancelled 0
# skipped 0
# todo 0
# duration_ms 511.118549

> Task :core:compileJava UP-TO-DATE
> Task :core:classes UP-TO-DATE
> Task :core-shared:spotbugsTest UP-TO-DATE
> Task :core-shared:pmdTest UP-TO-DATE
> Task :core:jar UP-TO-DATE
> Task :core:pmdMain UP-TO-DATE
> Task :core:spotbugsMain UP-TO-DATE
> Task :core-shared:test UP-TO-DATE

> Task :rest-api:eudiwConsoleJsTest
TAP version 13
# Subtest: EUDIW console renders multi-presentation sections with trace IDs and actions
ok 1 - EUDIW console renders multi-presentation sections with trace IDs and actions
  ---
  duration_ms: 60.291039
  type: 'test'
  ...
# Subtest: Deep-link parameters hydrate tab and mode state
ok 2 - Deep-link parameters hydrate tab and mode state
  ---
  duration_ms: 6.345078
  type: 'test'
  ...
# Subtest: syncDeepLink pushes alias URLs into history
ok 3 - syncDeepLink pushes alias URLs into history
  ---
  duration_ms: 16.501056
  type: 'test'
  ...
# Subtest: syncDeepLink preserves canonical protocol when no alias is provided
ok 4 - syncDeepLink preserves canonical protocol when no alias is provided
  ---
  duration_ms: 14.875708
  type: 'test'
  ...
# Subtest: initial URL search hydrates state without pushing history
ok 5 - initial URL search hydrates state without pushing history
  ---
  duration_ms: 22.421724
  type: 'test'
  ...
# Subtest: history state fallback tolerates partial state objects
ok 6 - history state fallback tolerates partial state objects
  ---
  duration_ms: 16.776271
  type: 'test'
  ...
# Subtest: protocol activation reuses the initial search state before rewrites
ok 7 - protocol activation reuses the initial search state before rewrites
  ---
  duration_ms: 19.3779
  type: 'test'
  ...
# Subtest: popstate restores prior alias, tab, and mode selections
ok 8 - popstate restores prior alias, tab, and mode selections
  ---
  duration_ms: 18.79803
  type: 'test'
  ...
1..8
# tests 8
# suites 0
# pass 8
# fail 0
# cancelled 0
# skipped 0
# todo 0
# duration_ms 769.199545

> Task :core:compileTestFixturesJava UP-TO-DATE
> Task :core:testFixturesClasses UP-TO-DATE
> Task :core-ocra:compileJava UP-TO-DATE
> Task :core-ocra:classes UP-TO-DATE
> Task :core:spotbugsTestFixtures UP-TO-DATE
> Task :core-ocra:jar UP-TO-DATE
> Task :core-ocra:spotbugsMain UP-TO-DATE
> Task :core-ocra:pmdMain UP-TO-DATE
> Task :ui:compileJava NO-SOURCE
> Task :ui:classes UP-TO-DATE
> Task :ui:compileTestJava NO-SOURCE
> Task :ui:pmdMain NO-SOURCE
> Task :ui:spotbugsMain NO-SOURCE
> Task :ui:checkstyleMain NO-SOURCE
> Task :ui:testClasses UP-TO-DATE
> Task :ui:jar UP-TO-DATE
> Task :ui:pmdTest NO-SOURCE
> Task :ui:checkstyleTest NO-SOURCE
> Task :ui:test NO-SOURCE
> Task :ui:spotbugsTest NO-SOURCE
> Task :spotlessJava UP-TO-DATE
> Task :core-shared:pmdMain UP-TO-DATE
> Task :spotlessJavaApply UP-TO-DATE
> Task :ui:check UP-TO-DATE
> Task :spotlessJavaCheck UP-TO-DATE
> Task :infra-persistence:compileJava UP-TO-DATE
> Task :infra-persistence:classes UP-TO-DATE
> Task :core-ocra:compileTestJava UP-TO-DATE
> Task :core-ocra:testClasses UP-TO-DATE
> Task :core:compileTestJava UP-TO-DATE
> Task :core:testClasses UP-TO-DATE
> Task :infra-persistence:jar UP-TO-DATE
> Task :infra-persistence:spotbugsMain UP-TO-DATE
> Task :core-ocra:pmdTest UP-TO-DATE
> Task :core:spotbugsTest UP-TO-DATE
> Task :infra-persistence:compileTestJava UP-TO-DATE
> Task :spotlessKotlinGradle UP-TO-DATE
> Task :infra-persistence:pmdMain UP-TO-DATE
> Task :infra-persistence:testClasses UP-TO-DATE
> Task :spotlessKotlinGradleApply UP-TO-DATE
> Task :core-ocra:spotbugsTest UP-TO-DATE
> Task :core:pmdTestFixtures UP-TO-DATE
> Task :core:testFixturesJar UP-TO-DATE
> Task :core:checkstyleTest UP-TO-DATE
> Task :core-shared:checkstyleTest UP-TO-DATE
> Task :core:checkstyleTestFixtures UP-TO-DATE
> Task :spotlessKotlinGradleCheck UP-TO-DATE
> Task :core-shared:checkstyleMain UP-TO-DATE
> Task :core-ocra:checkstyleMain UP-TO-DATE
> Task :core-ocra:checkstyleTest UP-TO-DATE
> Task :core:checkstyleMain UP-TO-DATE
> Task :spotlessMisc UP-TO-DATE
> Task :spotlessMiscApply UP-TO-DATE
> Task :core-shared:check UP-TO-DATE
> Task :spotlessApply UP-TO-DATE
> Task :infra-persistence:spotbugsTest UP-TO-DATE
> Task :infra-persistence:checkstyleMain UP-TO-DATE
> Task :spotlessMiscCheck UP-TO-DATE
> Task :spotlessCheck UP-TO-DATE
> Task :infra-persistence:checkstyleTest UP-TO-DATE
> Task :infra-persistence:pmdTest UP-TO-DATE
> Task :core:pmdTest UP-TO-DATE
> Task :core-ocra:test UP-TO-DATE
> Task :infra-persistence:test UP-TO-DATE
> Task :core-ocra:check UP-TO-DATE
> Task :infra-persistence:check UP-TO-DATE
> Task :core:test UP-TO-DATE
> Task :core:check UP-TO-DATE
> Task :application:compileJava UP-TO-DATE
> Task :application:classes UP-TO-DATE
> Task :application:jar UP-TO-DATE
> Task :application:checkstyleMain UP-TO-DATE
> Task :application:pmdMain UP-TO-DATE
> Task :cli:compileJava UP-TO-DATE
> Task :cli:classes UP-TO-DATE
> Task :cli:jar UP-TO-DATE
> Task :cli:checkstyleMain UP-TO-DATE
> Task :application:spotbugsMain UP-TO-DATE
> Task :cli:pmdMain UP-TO-DATE
> Task :cli:spotbugsMain UP-TO-DATE
> Task :cli:compileTestJava UP-TO-DATE
> Task :cli:testClasses UP-TO-DATE
> Task :rest-api:compileJava UP-TO-DATE
> Task :rest-api:classes UP-TO-DATE
> Task :cli:spotbugsTest UP-TO-DATE
> Task :cli:pmdTest UP-TO-DATE
> Task :cli:checkstyleTest UP-TO-DATE
> Task :rest-api:jar UP-TO-DATE
> Task :rest-api:pmdMain UP-TO-DATE
> Task :rest-api:checkstyleMain UP-TO-DATE
> Task :cli:test UP-TO-DATE
> Task :cli:check UP-TO-DATE
> Task :application:compileTestJava UP-TO-DATE
> Task :application:testClasses UP-TO-DATE
> Task :core-architecture-tests:compileTestJava UP-TO-DATE
> Task :rest-api:spotbugsMain UP-TO-DATE
> Task :core-architecture-tests:testClasses UP-TO-DATE
> Task :application:pmdTest UP-TO-DATE
> Task :application:checkstyleTest UP-TO-DATE
> Task :core-architecture-tests:checkstyleTest UP-TO-DATE
> Task :application:spotbugsTest UP-TO-DATE
> Task :core-architecture-tests:spotbugsTest UP-TO-DATE
> Task :core-architecture-tests:pmdTest UP-TO-DATE
> Task :core-architecture-tests:test UP-TO-DATE
> Task :core-architecture-tests:check UP-TO-DATE
> Task :architectureTest UP-TO-DATE
> Task :application:test UP-TO-DATE
> Task :application:check UP-TO-DATE
> Task :rest-api:compileTestJava UP-TO-DATE
> Task :rest-api:testClasses UP-TO-DATE
> Task :rest-api:checkstyleTest UP-TO-DATE
> Task :rest-api:pmdTest UP-TO-DATE
> Task :rest-api:spotbugsTest UP-TO-DATE
> Task :rest-api:test UP-TO-DATE
> Task :rest-api:check
> Task :jacocoAggregatedReport UP-TO-DATE
> Task :jacocoCoverageVerification UP-TO-DATE
> Task :check UP-TO-DATE

BUILD SUCCESSFUL in 19s
96 actionable tasks: 2 executed, 94 up-to-date
Configuration cache entry reused.
  - To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/9.2.0/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 
Calculating task graph as no cached configuration is available for tasks: qualityGate
Type-safe project accessors is an incubating feature.
> Task :infra-persistence:processResources NO-SOURCE
> Task :application:processTestResources NO-SOURCE
> Task :application:processResources NO-SOURCE
> Task :cli:processResources NO-SOURCE
> Task :core-architecture-tests:processResources NO-SOURCE
> Task :core-architecture-tests:processTestResources NO-SOURCE
> Task :cli:processTestResources NO-SOURCE
> Task :ui:processResources NO-SOURCE
> Task :core:processTestResources NO-SOURCE
> Task :core:processTestFixturesResources NO-SOURCE
> Task :core-shared:processResources NO-SOURCE
> Task :rest-api:processTestResources NO-SOURCE
> Task :core:processResources NO-SOURCE
> Task :core-architecture-tests:compileJava NO-SOURCE
> Task :core-ocra:processTestResources NO-SOURCE
> Task :core-ocra:processResources NO-SOURCE
> Task :core-architecture-tests:classes UP-TO-DATE
> Task :rest-api:processResources UP-TO-DATE
> Task :spotlessInternalRegisterDependencies UP-TO-DATE
> Task :core-shared:compileJava UP-TO-DATE
> Task :core-shared:classes UP-TO-DATE
> Task :core-shared:jar UP-TO-DATE
> Task :reflectionScan
> Task :core:compileJava UP-TO-DATE
> Task :core:classes UP-TO-DATE
> Task :core:jar UP-TO-DATE
> Task :spotlessJava UP-TO-DATE
> Task :spotlessJavaCheck UP-TO-DATE
> Task :core:compileTestFixturesJava UP-TO-DATE
> Task :core-ocra:compileJava UP-TO-DATE
> Task :core-ocra:classes UP-TO-DATE
> Task :core:testFixturesClasses UP-TO-DATE
> Task :core-ocra:jar UP-TO-DATE
> Task :ui:compileJava NO-SOURCE
> Task :ui:classes UP-TO-DATE
> Task :ui:jar UP-TO-DATE
> Task :core:compileTestJava UP-TO-DATE
> Task :infra-persistence:compileJava UP-TO-DATE
> Task :spotlessKotlinGradle UP-TO-DATE
> Task :infra-persistence:classes UP-TO-DATE
> Task :core-ocra:compileTestJava UP-TO-DATE
> Task :core-ocra:testClasses UP-TO-DATE
> Task :core:testClasses UP-TO-DATE
> Task :core:testFixturesJar UP-TO-DATE
> Task :spotlessMisc UP-TO-DATE
> Task :spotlessKotlinGradleCheck UP-TO-DATE
> Task :spotlessMiscCheck UP-TO-DATE
> Task :spotlessCheck UP-TO-DATE
> Task :infra-persistence:jar UP-TO-DATE
> Task :core-ocra:test UP-TO-DATE
> Task :core:test UP-TO-DATE
> Task :application:compileJava UP-TO-DATE
> Task :application:classes UP-TO-DATE
> Task :application:jar UP-TO-DATE
> Task :cli:compileJava UP-TO-DATE
> Task :cli:classes UP-TO-DATE
> Task :cli:jar UP-TO-DATE
> Task :cli:compileTestJava UP-TO-DATE
> Task :cli:testClasses UP-TO-DATE
> Task :cli:test UP-TO-DATE
> Task :rest-api:compileJava UP-TO-DATE
> Task :rest-api:classes UP-TO-DATE
> Task :rest-api:jar UP-TO-DATE
> Task :application:compileTestJava UP-TO-DATE
> Task :application:testClasses UP-TO-DATE
> Task :core-architecture-tests:compileTestJava UP-TO-DATE
> Task :core-architecture-tests:testClasses UP-TO-DATE
> Task :core-architecture-tests:test UP-TO-DATE
> Task :architectureTest UP-TO-DATE
> Task :application:test UP-TO-DATE
> Task :rest-api:compileTestJava UP-TO-DATE
> Task :rest-api:testClasses UP-TO-DATE
> Task :rest-api:test UP-TO-DATE
> Task :jacocoAggregatedReport UP-TO-DATE
> Task :jacocoCoverageVerification UP-TO-DATE
> Task :check UP-TO-DATE
> Task :qualityGate

BUILD SUCCESSFUL in 28s
40 actionable tasks: 1 executed, 39 up-to-date
Configuration cache entry stored.

- [x] T1309 – Audit CLI modules for other command hierarchies that warrant sealing (S13-01).
  _Intent:_ Document follow-ups or confirm no additional work is needed.
  _Verification commands:_
  - cli/src/main/java/io/openauth/sim/cli/EmvCli.java:161:    static final class CapCommand implements java.util.concurrent.Callable<Integer> {
cli/src/main/java/io/openauth/sim/cli/EmvCli.java:206:        static final class SeedCommand implements Callable<Integer> {
cli/src/main/java/io/openauth/sim/cli/EmvCli.java:268:        static final class ReplayCommand implements Callable<Integer> {
cli/src/main/java/io/openauth/sim/cli/EmvCli.java:798:        static final class EvaluateStoredCommand implements java.util.concurrent.Callable<Integer> {
cli/src/main/java/io/openauth/sim/cli/EmvCli.java:1208:        static final class EvaluateCommand implements java.util.concurrent.Callable<Integer> {
cli/src/main/java/io/openauth/sim/cli/HotpCli.java:122:    abstract static class AbstractHotpCommand implements Callable<Integer> {
cli/src/main/java/io/openauth/sim/cli/HotpCli.java:145:    static final class ImportCommand extends AbstractHotpCommand {
cli/src/main/java/io/openauth/sim/cli/HotpCli.java:225:    static final class ListCommand extends AbstractHotpCommand {
cli/src/main/java/io/openauth/sim/cli/HotpCli.java:265:    static final class EvaluateCommand extends AbstractHotpCommand {
cli/src/main/java/io/openauth/sim/cli/TotpCli.java:128:    private abstract static class AbstractTotpCommand implements Callable<Integer> {
cli/src/main/java/io/openauth/sim/cli/TotpCli.java:159:    static final class ListCommand extends AbstractTotpCommand {
cli/src/main/java/io/openauth/sim/cli/TotpCli.java:199:    static final class EvaluateStoredCommand extends AbstractTotpCommand {
cli/src/main/java/io/openauth/sim/cli/TotpCli.java:313:    static final class EvaluateInlineCommand extends AbstractTotpCommand {
cli/src/main/java/io/openauth/sim/cli/Fido2Cli.java:568:    static final class EvaluateStoredCommand extends AbstractFido2Command {
cli/src/main/java/io/openauth/sim/cli/Fido2Cli.java:715:    static final class EvaluateInlineCommand extends AbstractFido2Command {
cli/src/main/java/io/openauth/sim/cli/Fido2Cli.java:923:    static final class ReplayStoredCommand extends AbstractFido2Command {
cli/src/main/java/io/openauth/sim/cli/Fido2Cli.java:1100:    static final class AttestCommand extends AbstractFido2Command {
cli/src/main/java/io/openauth/sim/cli/Fido2Cli.java:1607:    static final class AttestReplayCommand extends AbstractFido2Command {
cli/src/main/java/io/openauth/sim/cli/Fido2Cli.java:1961:    static final class SeedAttestationsCommand extends AbstractFido2Command {
cli/src/main/java/io/openauth/sim/cli/Fido2Cli.java:2132:    static final class VectorsCommand extends AbstractFido2Command {
cli/src/main/java/io/openauth/sim/cli/Fido2Cli.java:2141:    private abstract static class AbstractFido2Command implements java.util.concurrent.Callable<Integer> {
cli/src/main/java/io/openauth/sim/cli/eudi/openid4vp/EudiwCli.java:130:    static final class RequestCommand implements java.util.concurrent.Callable<Integer> {
cli/src/main/java/io/openauth/sim/cli/eudi/openid4vp/EudiwCli.java:145:        static final class CreateCommand implements java.util.concurrent.Callable<Integer> {
cli/src/main/java/io/openauth/sim/cli/eudi/openid4vp/EudiwCli.java:222:    static final class WalletCommand implements java.util.concurrent.Callable<Integer> {
cli/src/main/java/io/openauth/sim/cli/eudi/openid4vp/EudiwCli.java:237:        static final class SimulateCommand implements java.util.concurrent.Callable<Integer> {
cli/src/main/java/io/openauth/sim/cli/eudi/openid4vp/EudiwCli.java:327:    static final class ValidateCommand implements java.util.concurrent.Callable<Integer> {
cli/src/main/java/io/openauth/sim/cli/OcraCli.java:229:    abstract static sealed class AbstractOcraCommand implements Callable<Integer>
cli/src/main/java/io/openauth/sim/cli/OcraCli.java:310:    static final class ImportCommand extends AbstractOcraCommand {
cli/src/main/java/io/openauth/sim/cli/OcraCli.java:388:    static final class ListCommand extends AbstractOcraCommand {
cli/src/main/java/io/openauth/sim/cli/OcraCli.java:438:    static final class DeleteCommand extends AbstractOcraCommand {
cli/src/main/java/io/openauth/sim/cli/OcraCli.java:468:    static final class EvaluateCommand extends AbstractOcraCommand {
cli/src/main/java/io/openauth/sim/cli/OcraCli.java:641:    static final class VerifyCommand extends AbstractOcraCommand {

- [x] T1310 – Audit REST controllers for escaped JSON example strings and confirm text block adoption policy (S13-03).
  _Intent:_ Ensure future endpoints follow the text block convention by default.
  _Verification commands:_
  - rest-api/src/main/java/io/openauth/sim/rest/emv/cap/EmvCapTelemetryPayload.java:16:        this.frame = Objects.requireNonNull(frame, "frame");
rest-api/src/main/java/io/openauth/sim/rest/emv/cap/EmvCapTelemetryPayload.java:17:        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
rest-api/src/main/java/io/openauth/sim/rest/emv/cap/EmvCapTelemetryPayload.java:20:    @JsonProperty("event")
rest-api/src/main/java/io/openauth/sim/rest/emv/cap/EmvCapTelemetryPayload.java:25:    @JsonProperty("status")
rest-api/src/main/java/io/openauth/sim/rest/emv/cap/EmvCapTelemetryPayload.java:30:    @JsonProperty("reasonCode")
rest-api/src/main/java/io/openauth/sim/rest/emv/cap/EmvCapTelemetryPayload.java:35:    @JsonProperty("sanitized")
rest-api/src/main/java/io/openauth/sim/rest/emv/cap/EmvCapTelemetryPayload.java:40:    @JsonProperty("fields")
rest-api/src/main/java/io/openauth/sim/rest/emv/cap/EmvCapReplayMetadata.java:8:        @JsonProperty("credentialSource") String credentialSource,
rest-api/src/main/java/io/openauth/sim/rest/emv/cap/EmvCapReplayMetadata.java:9:        @JsonProperty("credentialId") String credentialId,
rest-api/src/main/java/io/openauth/sim/rest/emv/cap/EmvCapReplayMetadata.java:10:        @JsonProperty("mode") String mode,

## Notes / TODOs
- Follow-up audits should convert any newly added command hierarchies/example payloads to sealed classes/text blocks immediately.
