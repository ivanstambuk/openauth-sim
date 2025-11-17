# Feature 015 Tasks – MCP Agent Facade

_Status:_ Draft  
_Last updated:_ 2025-11-16_

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-015-01`), non-goal IDs (`NFR-015-01`), and scenario IDs (`S-015-01`) inside the same parentheses immediately after the task title (omit categories that do not apply).
> When new high- or medium-impact questions arise during execution, add them to [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md) instead of informal notes, and treat a task as fully resolved only once the governing spec sections reflect the clarified behaviour.

## Checklist
- [ ] T-015-01 – Enrich MCP tool catalogue metadata (FR-015-01, S-015-01).  
  _Intent:_ Add JSON Schema, prompt hints, and version metadata for every MCP tool; emit `mcp.catalog.listed` telemetry; update README/AGENTS catalogue section.  
  _Verification commands:_  
  - `./gradlew --no-daemon :tools-mcp-server:test --tests "*Catalog*"`  
  - `./gradlew --no-daemon generateJsonLd check`  
  _Notes:_ Update `build/mcp-feedback.json` with catalogue output and log telemetry IDs in `_current-session.md`.

- [ ] T-015-02 – Implement MCP helper flows (fixtures + TOTP) (FR-015-02, S-015-02, NFR-015-01).  
  _Intent:_ Finalize helper service contracts, wire MCP handlers, ensure payload redaction/telemetry, and extend transcripts/docs.  
  _Verification commands:_  
  - `./gradlew --no-daemon :application:test --tests "*Helper*"`  
  - `./gradlew --no-daemon :rest-api:test --tests "*Helper*"`  
  - `./gradlew --no-daemon :tools-mcp-server:test --tests "*Helper*"`  
  - `./gradlew --no-daemon generateJsonLd check`  
  _Notes:_ Add documentation snippets in README/AGENTS/how-to; sanitize transcripts.

- [ ] T-015-03 – Add session/capability tools (FR-015-03, S-015-03).  
  _Intent:_ Ship `session.describe`, `session.reset`, and `capabilities.describe` MCP tools with telemetry + docs.  
  _Verification commands:_  
  - `./gradlew --no-daemon :tools-mcp-server:test --tests "*Session*"`  
  - `./gradlew --no-daemon generateJsonLd check`  
  _Notes:_ Update `build/mcp-feedback.json` with session flows and document governance expectations.

- [ ] T-015-04 – Rate limits, auditing, and governance updates (FR-015-04, NFR-015-02..04, S-015-04).  
  _Intent:_ Implement MCP rate-limit middleware, audit logging, and telemetry; run full `qualityGate` and update roadmap/knowledge map/how-to docs.  
  _Verification commands:_  
  - `./gradlew --no-daemon qualityGate`  
  - `./gradlew --no-daemon reflectionScan`  
  - `./gradlew --no-daemon generateJsonLd check`  
  _Notes:_ Record governance logs in `_current-session.md`; refresh `build/mcp-feedback.json`; execute Implementation Drift Gate checklist.

## Verification Log (Optional)
- _TBD (populate as work progresses)._ 

## Notes / TODOs
- Track additional helper ideas (HOTP counter peek, EMV preset inspector, FIDO2 attestation summaries) as follow-ups once the core facade ships.
