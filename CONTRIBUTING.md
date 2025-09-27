# Contributing

Thanks for helping shape OpenAuth Simulator! We build this project in deliberately tiny, verifiable increments. Please follow these guardrails to keep agent-driven development predictable.

## Workflow expectations

- **Small steps.** Aim for units of work that you can complete (including tests and docs) in under ten minutes. If a change is larger, decompose it and land the pieces sequentially.
- **Tests first, always.** Run `JAVA_HOME="$PWD/.jdks/jdk-17.0.16+8" ./gradlew spotlessApply check` after every change. Green builds are mandatory; if you must defer a failing test, disable it with a TODO and reference the follow-up issue/ADR.
- **Conventional commits.** Use the Angular-style prefixes (`feat:`, `fix:`, `build:`, etc.) so release automation and changelogs can stay machine-readable.
- **Keep docs in sync.** Update the relevant section under `docs/` whenever you introduce or change behaviour (overview, architecture notes, ADRs, runbooks, etc.).
- **Own the ADRs.** Decisions—especially reversals—must be captured under `docs/6-decisions/` before or alongside the implementation.
- **Error Prone toggle.** Until the Gradle plugin supports the latest Error Prone release, the build runs with Error Prone disabled. Feel free to re-enable it locally via `-PerrorproneEnabled=true`; please include any required fixes in the same commit.

## Branching & reviews

- Work on feature branches, submit pull requests against `main`, and request review from another maintainer or agent.
- Surface assumptions explicitly in PR descriptions, especially around cryptographic behaviours or protocol compliance.
- Include reproduction steps and relevant screenshots/log excerpts when fixing defects.

## Coding style

- Java 17 (records, sealed types, pattern matching) is encouraged; avoid using APIs introduced after Java 17 unless wrapped behind compatibility layers.
- Follow Checkstyle and Spotless; the CI will fail on formatting issues.
- Avoid committing generated artifacts, secrets, or local database files. The default `.gitignore` already filters most build outputs.

## Security & compliance

- Never place real credential material in the repository. Use synthetic fixtures and document their purpose in `docs/8-compliance`.
- Dependencies are vulnerability-scanned via OWASP Dependency Check—investigate high/critical findings promptly.

## Questions & suggestions

Open a GitHub discussion or issue, or file an ADR draft in `docs/6-decisions` if you want to propose architectural changes. The more context you leave behind, the smoother future agent handoffs become.
