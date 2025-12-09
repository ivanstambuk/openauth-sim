# ADR-0012: CLI Evaluate Command Unification (Stored vs Inline Toggle)

- **Status:** Accepted
- **Date:** 2025-12-08
- **Related features/specs:** Feature 002 (docs/4-architecture/features/002/spec.md), Feature 004 (docs/4-architecture/features/004/spec.md), Feature 013 (docs/4-architecture/features/013/spec.md)
- **Related open questions:** None

## Context

CLI evaluate flows were split between `evaluate` (stored credentials) and `evaluate-inline` (inline parameters) for some protocols. This created parallel command trees, duplicated help/docs/tests, and diverged from the FIDO2 CLI pattern recently unified under a single `evaluate` subcommand with stored-vs-inline detection. We want a consistent rule for all CLI protocols that keeps telemetry/preview behaviour unchanged while simplifying operator ergonomics and documentation.

Affected modules: `cli` (TOTP now, FIDO2 already aligned), `standalone` (picocli shading). REST/UI facades remain unchanged by this decision.

## Decision

- Each protocol CLI exposes a single `evaluate` subcommand. Presence of `--credential-id` selects the **stored** path; absence selects the **inline** path.
- Inline-only options (e.g., secrets, algorithms) are rejected when `--credential-id` is provided; stored-only options keep their existing semantics.
- Separate `evaluate-inline` subcommands are removed going forward; future CLI protocols must follow this toggle pattern.
- Telemetry fields continue to emit `credentialReference=true|false`, `credentialId` when available, and existing preview/verbose output contracts are preserved.

## Consequences

### Positive
- Simpler CLI UX and help output (one entry point per protocol).
- Reduces duplicated tests/docs/help snapshots and aligns with the FIDO2 CLI pattern.
- Clear toggle rule (`--credential-id` present ⇒ stored) is easy to document and reuse across protocols.

### Negative
- Scripts invoking deprecated `evaluate-inline` subcommands must switch to `evaluate` without `--credential-id`.
- Minor retraining for operators accustomed to the old split commands.

## Alternatives Considered

- **Option A (chosen):** Single `evaluate` command with `--credential-id` toggle. Pros: simple mental model, minimal flags. Cons: breaking change for callers of removed subcommands.
- **Option B:** Keep separate `evaluate` and `evaluate-inline` commands. Pros: explicit modes. Cons: duplicated code/docs, inconsistent with FIDO2, harder to maintain.
- **Option C:** Add an explicit `--mode stored|inline` flag. Pros: explicit, scriptable. Cons: more flags to teach, still requires validating mutually exclusive options.

## Security / Privacy Impact

- No new secret handling surfaces; inline secrets remain required explicitly and are never logged.
- Telemetry redaction and preview/verbose trace behaviour remain unchanged.

## Operational Impact

- Smaller CLI help surface and fewer docs/test snapshots to maintain.
- Standalone shaded JAR continues to include picocli; no additional dependencies or build steps.

## Links

- Related spec sections: `docs/4-architecture/features/002/spec.md` (TOTP CLI), `docs/4-architecture/features/004/spec.md` (FIDO2 CLI), `docs/4-architecture/features/013/spec.md` (CLI/tooling guardrails)
- Related ADRs: ADR-0008 (Native Java Javadoc CI strategy)
