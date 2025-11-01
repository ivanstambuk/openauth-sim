# Feature Plan 038 – Evaluation Result Preview Table

_Linked specification:_ `docs/4-architecture/specs/feature-038-result-preview-window.md`  
_Status:_ In progress  
_Last updated:_ 2025-11-01

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
1. **I1 – REST schema scaffolding** _(in progress 2025-11-01)_  
   - Add request/response window models (HOTP/TOTP/OCRA), remove evaluation drift fields, and wire service tests.  
   - Update OpenAPI snapshot.  
   - Commands: `./gradlew --no-daemon :rest-api:test`.
2. **I2 – Application/CLI contract alignment**  
   - Expose preview data from application services; add CLI window flags, drop evaluation drift options, and extend tests.  
   - Commands: `./gradlew --no-daemon :application:test :cli:test`.
3. **I3 – Operator UI table integration**  
   - Render preview table inside result card with accent bar highlight; replace evaluation drift controls with unified window inputs while leaving replay drift untouched; update Selenium coverage.  
   - Commands: `./gradlew --no-daemon :rest-api:test :ui:test`.
4. **I4 – Accessibility & docs**  
   - Verify screen-reader/contrast behaviour, document the change, refresh knowledge map if needed.  
   - Commands: `./gradlew --no-daemon spotlessApply check`.

## Risks & Mitigations
- **Contract regressions:** cover with REST integration and CLI snapshot tests.  
- **UI overflow:** design table to collapse gracefully on narrow widths; add responsive checks.  
- **Accessibility:** ensure accent bar combined with Δ=0 label satisfies WCAG via tests/manual audit.

## Exit Criteria
- All increments merged with green build (`./gradlew --no-daemon :application:test :cli:test :rest-api:test :ui:test pmdMain pmdTest spotlessApply check`).  
- Operator docs updated to describe preview visibility and accent highlight.  
- Roadmap entry marked “In progress” with outcome documented.
