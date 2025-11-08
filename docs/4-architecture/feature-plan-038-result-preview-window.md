# Feature Plan 038 – Evaluation Result Preview Table

_Linked specification:_ `docs/4-architecture/specs/feature-038-result-preview-window.md`  
_Status:_ Complete  
_Last updated:_ 2025-11-08 (I5 helper-text cleanup delivered)

## Vision & Success Criteria
- Surface evaluation previews (Δ-ordered OTPs) directly in REST payloads, operator UI result cards, and CLI outputs.
- Maintain backward compatibility for existing callers while enriching telemetry and accessibility affordances.
- Deliver a consistent highlight treatment (accent bar) for the evaluated row across protocols.

## Scope Alignment
- **In scope:** REST response contract updates, OpenAPI regeneration, UI layout tweaks, CLI table formatting, accessibility checks, documentation refresh.
- **Out of scope:** Replay result cards, changes to offset input widgets, persistence/history of previews.

## Dependencies & Interfaces
- Builds on Feature 037 Base32 inline secret support (shared DTOs).  
- Reuses application-level evaluation services for HOTP/TOTP/OCRA.  
- Requires UI table components (operator console) and CLI formatting helpers.

## Increment Breakdown (≤30 minutes each)
1. **I1 – REST schema scaffolding** _(completed 2025-11-02)_  
   - Add request/response window models (HOTP/TOTP/OCRA), remove evaluation drift fields, and wire service tests.  
   - Update OpenAPI snapshot.  
   - Commands: `./gradlew --no-daemon :rest-api:test`.
2. **I2 – Application/CLI contract alignment** _(completed 2025-11-01)_  
   - Expose preview data from application services; add CLI window flags, drop evaluation drift options, render preview tables, and extend HOTP/TOTP/OCRA unit coverage.  
   - Commands: `./gradlew --no-daemon :cli:test`, `./gradlew --no-daemon spotlessApply check`.  
3. **I3 – Operator UI table integration** _(completed 2025-11-01)_  
   - Rendered preview tables for HOTP/TOTP/OCRA evaluation cards with protocol accent bar, introduced `Preview window offsets` controls in stored/inline forms, preserved replay drift inputs, and refreshed Selenium coverage for offsets {0,0} and {1,1}.  
   - Commands: `./gradlew --no-daemon :rest-api:test :ui:test`.
4. **I4 – Accessibility & docs** _(completed 2025-11-01)_  
   - Finalised accent styling via inset highlight, confirmed accessibility review (Δ = 0 remains perceivable without colour), refreshed Selenium coverage, updated operator how-to guides/roadmap/knowledge map, and prepared for full quality gate rerun.  
   - Commands: `./gradlew --no-daemon :rest-api:test`, `./gradlew --no-daemon spotlessApply check`.
5. **I5 – Preview offset helper-text cleanup** _(completed 2025-11-08)_  
   - Removed the redundant descriptive sentence beneath the "Preview window offsets" controls on HOTP/TOTP/OCRA forms, dropped the unused `aria-describedby` bindings, and confirmed the aggregate `./gradlew --no-daemon spotlessApply check` pipeline (600 s timeout) stays green.  

## Risks & Mitigations
- **Contract regressions:** cover with REST integration and CLI snapshot tests.  
- **UI overflow:** design table to collapse gracefully on narrow widths; add responsive checks.  
- **Accessibility:** ensure accent bar combined with Δ=0 label satisfies WCAG via tests/manual audit.

## Exit Criteria
- All increments merged with green build (`./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`).  
- Operator docs updated to describe preview visibility and accent highlight.  
- Roadmap entry marked “Complete” with outcome documented.
