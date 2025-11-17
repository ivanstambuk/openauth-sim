# Decisions

We track significant choices as Architecture Decision Records (ADRs). Each ADR is immutable; superseded entries should point at their replacements.

Numbering convention: `ADR-XXXX` where `XXXX` is zero-padded and increments sequentially.

| ADR | Status   | Summary                            |
|-----|----------|------------------------------------|
| [ADR-0001](ADR-0001-core-storage.md) | Accepted | MapDB + Caffeine as the initial credential persistence stack |
| [ADR-0002](ADR-0002-governance-formatter-and-hooks.md) | Accepted | Palantir formatter pin + managed gitlint/Spotless hooks for governance |
| [ADR-0003](ADR-0003-governance-workflow-and-drift-gates.md) | Accepted | Centralised governance workflow tying Feature 011 to constitution gates |
| [ADR-0004](ADR-0004-docs-and-quality-gate-workflow.md) | Accepted | Feature 010 as owner of OCRA docs and aggregated qualityGate workflow |
| [ADR-0005](ADR-0005-operator-console-and-ui-contracts.md) | Accepted | Unified operator console at /ui/console with shared UI contracts and JS harness |
| [ADR-0006](ADR-0006-eudiw-openid4vp-architecture-and-wallet.md) | Accepted | Partitioned EUDIW OpenID4VP architecture across verifier, fixtures, and wallet features (006/007/008) |

## Templates

- Use [docs/templates/adr-template.md](docs/templates/adr-template.md) when creating new ADRs.
- Each ADR documents context, decision, consequences, alternatives, security/privacy, operational impact, and reference links, plus pointers to the affected specs/open-questions.
