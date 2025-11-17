# ADR-0005: Operator Console Layout and Shared UI Contracts

- **Status:** Accepted
- **Date:** 2025-11-15
- **Related features/specs:** Feature 009 ([docs/4-architecture/features/009/spec.md](docs/4-architecture/features/009/spec.md)), Feature 010 ([docs/4-architecture/features/010/spec.md](docs/4-architecture/features/010/spec.md))
- **Related open questions:** (none currently)

## Context

The OpenAuth Simulator exposes multi-protocol operator flows (HOTP, TOTP, OCRA, FIDO2/WebAuthn, EMV/CAP, and forthcoming
EUDIW wallets). Historically, operator UIs evolved per protocol, leading to inconsistent tab structures, deep links,
preset labels, verbose trace handling, and JavaScript testing approaches.

Feature 009 consolidates the operator console at /ui/console and describes a shared layout: deterministic tab order,
canonical entry point, query-parameter-based routing, a Protocol Info drawer, harmonised preset labels, Base32/hex helpers,
preview windows, and a modular JS harness. These decisions are architectural and span multiple protocols and modules
(`rest-api`, `application`, `cli`, docs), so they require a dedicated ADR.

## Decision

Adopt a unified operator console and shared UI contracts as follows:
- Console entry and routing:
  - /ui/console is the single operator-console entry point for HOTP, TOTP, OCRA, FIDO2/WebAuthn, EMV/CAP, and future EUDIW
    protocols; legacy `/ui/ocra/*` views redirect or return 404 to avoid drift.
  - Tab order is fixed to: HOTP → TOTP → OCRA → FIDO2/WebAuthn → EMV/CAP → EUDIW OpenID4VP → EUDIW ISO/IEC 18013-5 → EUDIW
    SIOPv2, with placeholder panels kept until each protocol ships.
  - Console state (protocol + tab + mode) is encoded in query parameters so deep links and history navigation behave
    consistently; the router supports legacy parameters only where explicitly documented.
- Shared UI contracts:
  - Protocol Info drawer is a JSON-driven, accessibility-aware panel triggered from beside the tablist, synchronised with
    tab selection, and exposed as an embeddable asset for other DOM integrations.
  - Preset labels use the `<scenario – key attributes>` pattern across HOTP/TOTP/OCRA/FIDO2, with seeded/stored catalogues kept
    in sync and labels referenced by documentation.
  - Validation helpers guarantee that invalid responses surface result cards and messages for HOTP/TOTP/OCRA/WebAuthn flows.
  - Verbose trace mode and tiers are shared across console, CLI, and REST, with console traces flowing through the shared
    `VerboseTraceConsole` and `TelemetryContracts` adapters.
  - Base32/hex secret handling and inline DTOs reuse the same encoding helpers and mutual-exclusion rules across facades.
  - Preview windows reuse a common layout and table structure for evaluation offsets/deltas.
- Testing harness:
  - Operator console JS modules are exercised via a modular Node harness (`operatorConsoleJsTest`) that shares fixtures
    with Selenium and JVM tests; it must remain deterministic and protocol-filterable.

Feature 009’s spec/plan/tasks are the authoritative description of these contracts at the UI level; this ADR captures the
architectural reasoning and cross-module impact.

## Consequences

### Positive
- Single, predictable operator console entry point and tab order across all supported and planned protocols.
- Consistent UX patterns for presets, validation, verbose traces, Base32 inputs, and preview windows, simplifying operator
  training and documentation.
- Shared JS harness and fixtures lower the cost of extending console behaviour to new protocols.
- Legacy `/ui/ocra/*` routes are phased out or redirected, reducing maintenance overhead and UX drift.

### Negative
- Changes to console layout, tab order, or shared helpers now require coordinated updates to templates, JS harness, tests, and this ADR.
- Placeholder panels must be maintained for not-yet-implemented protocols, which can introduce extra bookkeeping.

## Alternatives Considered

- **A – Per-protocol consoles**
  - Pros: Maximum flexibility for each protocol; easier to prototype in isolation.
  - Cons: Leads to divergent UX, inconsistent deep links, and duplicated JavaScript/testing infrastructure.
- **B – Multiple console entry points (e.g., /ui/ocra, /ui/fido2)**
  - Pros: Keep existing URLs; can gradually migrate protocols.
  - Cons: Encourages drift and makes it harder to know which entry point is canonical; complicates documentation.
- **C – Unified console at /ui/console with shared contracts (chosen)**
  - Pros: Centralises UX decisions; keeps specs, docs, and tests focused on one shell; easier to apply cross-cutting changes.

## Security / Privacy Impact

- Shared verbose trace and Base32/hex helpers must continue to respect redaction rules defined by `TelemetryContracts`,
  especially for tiered traces (`normal`, `educational`, `lab-secrets`).
- Centralised console reduces the surface for misconfigured traces or inconsistent secret handling across protocols.

## Operational Impact

- Operators and maintainers must:
  - Treat /ui/console as the single operator entry point when documenting or testing flows.
  - Use the shared UI patterns (Info drawer, presets, validation helper, trace tiers, Base32 helpers, preview tables) when
    extending the console or authoring documentation.
  - Keep Selenium, Node, and JVM integration tests aligned with the contracts defined in Feature 009 and this ADR.

## Links

- Related spec sections: `[docs/4-architecture/features/009/spec.md](docs/4-architecture/features/009/spec.md)#overview`, `#functional-requirements`,
  `#ui--interaction-mock-ups`, `#telemetry--observability`
- Related ADRs: ADR-0004 (Documentation & Aggregated Quality Gate Workflow)
- Related issues / PRs: (to be linked from future console/UI updates)

