# Feature 015 Tasks – MCP Agent Facade

_Status:_ Draft  
_Last updated:_ 2025-11-18_

> Keep this checklist aligned with the feature plan increments. Stage tests before implementation, record verification commands beside each task, and prefer bite-sized entries (≤90 minutes).
> When referencing requirements, keep feature IDs (`F-015-01`), non-goal IDs (`NFR-015-01`), and scenario IDs (`S-015-01`) inside the same parentheses immediately after the task title (omit categories that do not apply).
> When new high- or medium-impact questions arise during execution, add them to [docs/4-architecture/open-questions.md](docs/4-architecture/open-questions.md) instead of informal notes, and treat a task as fully resolved only once the governing spec sections reflect the clarified behaviour.

## Checklist
- [x] T-015-01 – Enrich MCP tool catalogue metadata (FR-015-01, S-015-01).  
  _Intent:_ Add JSON Schema, prompt hints, and version metadata for every MCP tool; emit `mcp.catalog.listed` telemetry; update README/AGENTS catalogue section.  
  _Verification commands:_  
  - `./gradlew --no-daemon :tools-mcp-server:test --tests "*Catalog*"`  
  - `./gradlew --no-daemon generateJsonLd check`  
  _Notes:_ Update `build/mcp-feedback.json` with catalogue output and log telemetry IDs in `_current-session.md`.

- [x] T-015-02 – Implement MCP helper flows (fixtures + TOTP) (FR-015-02, S-015-02, NFR-015-01).  
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

- [ ] T-015-04 – Auditing and governance updates (FR-015-04, NFR-015-02..04, S-015-04).  
  _Intent:_ Implement MCP proxy auditing and telemetry (no in-process rate limiting), then run full `qualityGate` and update roadmap/knowledge map/how-to docs per ADR-0013.  
  _Verification commands:_  
  - `./gradlew --no-daemon qualityGate`  
  - `./gradlew --no-daemon reflectionScan`  
  - `./gradlew --no-daemon generateJsonLd check`  
  _Notes:_ Record governance logs in `_current-session.md`; refresh `build/mcp-feedback.json`; execute Implementation Drift Gate checklist.

- [ ] T-015-08 – Enforce CLI `--output-json` parity across all protocol commands (ADR-0014).  
  _Intent:_ Coordinate cross-feature work so HOTP/TOTP/OCRA/FIDO2/EMV/EUDIW CLI commands expose a uniform `--output-json` flag and documented schemas; update MCP catalogue metadata accordingly.  
  _Verification commands:_  
  - `./gradlew --no-daemon :cli:test :standalone:jar`  
  - `./gradlew --no-daemon :tools-mcp-server:test`  
  _Notes:_ Ensure protocol feature specs/plans/tasks carry the corresponding tasks and reference ADR-0014.

- [ ] T-015-05 – EMV Preset Inspector tool (FR-015-05, S-015-05).  
  _Intent:_ Add `emv.cap.inspect` tool to decode credential descriptors/bitmaps into human-readable profiles.  
  _Verification commands:_  
  - `./gradlew --no-daemon :tools-mcp-server:test --tests "*EmvCapInspect*"`  

- [ ] T-015-06 – Detailed Trace Analysis tool (FR-015-06, S-015-06).  
  _Intent:_ Add `emv.cap.trace` (or enhance `evaluate`) to return exact CDOL1 buffer construction and pre-decimalization blocks.  
  _Verification commands:_  
  - `./gradlew --no-daemon :tools-mcp-server:test --tests "*EmvCapTrace*"`  

- [ ] T-015-07 – CAP-2 / TDS Support (FR-015-07, S-015-07).  
  _Intent:_ Extend `EmvCapEngine` to support Mode 2 (Token Data Set) signing and decoupled data.  
  _Verification commands:_  
  - `./gradlew --no-daemon :core:test --tests "*EmvCapEngine*"`  
  - `./gradlew --no-daemon :tools-mcp-server:test --tests "*EmvCap*"`

## Verification Log (Optional)
- 2025-11-18 (T-015-02 – helper flows): `./gradlew --no-daemon :application:test --tests "*Helper*"`, `./gradlew --no-daemon :rest-api:test --tests "*Helper*"`, `./gradlew --no-daemon :tools-mcp-server:test --tests "*Helper*"`, `./gradlew --no-daemon generateJsonLd check`, `./gradlew --no-daemon spotlessApply check` (all PASS – confirms TOTP helper service + REST endpoint remain green, MCP helper telemetry is covered by tests, and repository-wide gates stayed green after wiring `mcp.totp.helper.lookup`/`mcp.fixtures.list`).

## Notes / TODOs
- Track additional helper ideas (HOTP counter peek, EMV preset inspector, FIDO2 attestation summaries) as follow-ups once the core facade ships.
