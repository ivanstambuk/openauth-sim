# ADR-0007: Native Java API Facade Strategy

- **Status:** Accepted
- **Date:** 2025-11-15
- **Related features/specs:** Feature 001 (`docs/4-architecture/features/001/spec.md`), Feature 002 (`docs/4-architecture/features/002/spec.md`), Feature 003 (`docs/4-architecture/features/003/spec.md`), Feature 004 (`docs/4-architecture/features/004/spec.md`), Feature 005 (`docs/4-architecture/features/005/spec.md`), Feature 006 (`docs/4-architecture/features/006/spec.md`), Feature 010 (`docs/4-architecture/features/010/spec.md`)
- **Related open questions:** (resolved) Native Java API facade governance (Feature 010)

## Context

OpenAuth Simulator currently exposes three well-documented facades for most protocols: CLI commands, REST API endpoints, and an
operator console web UI. Feature 003 already documents an OCRA Native Java API via `OcraCredentialFactory` +
`OcraResponseCalculator` and `docs/2-how-to/use-ocra-from-java.md`, but other protocols (HOTP, TOTP, FIDO2/WebAuthn, EMV/CAP,
EUDIW) do not yet have an equally formalised Java surface. At the same time, the project constitution and governance docs
refer to “programmatic” or “Native Java” as a facade alongside CLI/REST/UI, and DeepWiki/README now describe four
consumption surfaces.

We need a consistent architectural strategy for the Native Java API so that:
- Each protocol’s Java entry points are discoverable and documented.
- Stability expectations are clear (greenfield but curated).
- DeepWiki and how-to guides can treat Native Java consistently across protocols.

The decision must apply across modules: core (low-level helpers), application (orchestrating services), and consuming
facades (CLI/REST/UI) that may reuse the same Java seams.

## Decision

Adopt a **per-protocol Native Java API** strategy governed by cross-cutting guidelines rather than a single monolithic
cross-protocol Java SDK:

- Each protocol feature (001 HOTP, 002 TOTP, 003 OCRA, 004 FIDO2/WebAuthn, 005 EMV/CAP, 006 EUDIW OpenID4VP, and future
  protocols) must define one or more **Native Java API entry points** in its spec/plan/tasks, exposing a small set of
  well-typed services or helpers suitable for in-process Java consumption.
- These entry points typically live in `core` (for low-level crypto/fixture helpers) or `application` (for orchestration
  services) and are treated as **facade seams**: public Java APIs intended for direct use by external applications.
- OCRA’s existing Native Java API (OcraCredentialFactory + OcraResponseCalculator) remains the reference pattern; other
  protocols should mirror its approach by providing:
  - A stable entry type (e.g., `*ApplicationService` or equivalent) with explicit input/output DTOs.
  - Supporting helpers for constructing descriptors or requests.
  - At least one `docs/2-how-to/*-from-java.md` guide demonstrating end-to-end usage.
- Governance for these Native Java seams will be centralised in a future cross-cutting feature (e.g., Feature 014 –
  Native Java API Facade) that documents:
  - Naming/location conventions for public Java APIs.
  - Stability expectations relative to CLI/REST/UI.
  - Javadoc generation and publishing strategy.
  - How `*-from-java` guides and DeepWiki pages stay aligned with the curated APIs.

We explicitly **do not** introduce a single cross-protocol “Java SDK” facade module at this stage. Instead, we treat the
Native Java API as a cross-cutting concern implemented per protocol, with common governance documented here and in the
future Feature 014 spec.

## Consequences

### Positive
- Aligns documentation and governance with how the code is structured today: each protocol owns its domain logic and can
  expose a Native Java API that matches its needs.
- Keeps coupling low: changing EMV/CAP Java APIs does not require updating a shared cross-protocol SDK surface.
- Provides a clear contract for future work: Features 001–006 can add or refine their Native Java APIs incrementally,
  while DeepWiki and how-to guides treat Native Java as a first-class facade alongside CLI/REST/UI.
- Makes it straightforward to add a higher-level Java SDK later if needed, by layering it on top of these protocol-specific seams.

### Negative
- Consumers must understand per-protocol entry points rather than relying on a single `OpenAuthSimulator` facade.
- Each feature team must invest in designing and documenting its own Native Java API surface, which increases upfront
  documentation/test work compared to only having CLI/REST/UI.
- Without careful coordination, naming and style can drift across protocol APIs; Feature 014 and related guidelines must
  keep them coherent.

## Alternatives Considered

- **A – OCRA-only Native Java API (no cross-protocol mandate)**
  - Pros: No additional design or documentation work for other protocols.
  - Cons: Undermines the “four facades” story; Native Java remains OCRA-specific and ad-hoc for other domains.

- **B – Single cross-protocol Java SDK module**
  - Pros: Very simple for consumers; one entry point and DTO set.
  - Cons: Strong coupling across protocols, harder to evolve, and misaligned with the existing feature/module boundaries.

- **C – Per-protocol Native Java APIs governed by cross-cutting rules (chosen)**
  - Pros: Matches feature ownership, keeps coupling low, and allows incremental adoption while still treating Native Java as a first-class facade.
  - Cons: Requires cross-feature coordination and governance to keep APIs discoverable and coherent.

## Security / Privacy Impact

- Native Java APIs must honour the same secret-handling and redaction rules as other facades:
  - No exposure of raw secrets in return types or exceptions.
  - Clear documentation of which fields may contain sensitive material and how callers should handle them.
- Protocol-specific Native Java APIs should reuse existing telemetry/redaction helpers (e.g., TelemetryContracts,
  VerboseTrace) rather than inventing new logging patterns.

## Operational Impact

- Runbooks and how-to guides must treat Native Java as a supported consumption surface:
  - `docs/2-how-to/*-from-java.md` guides should be referenced from CLI/REST/UI docs where appropriate.
  - DeepWiki’s “Native Java API” page will point to per-protocol guides and Javadoc once published.
- Build/CI pipelines may need additional Javadoc generation and publication steps once Feature 014 is defined; this ADR
  does not mandate a specific publication mechanism but expects it to be captured there.

## Links

- Related spec sections: `docs/2-how-to/use-ocra-from-java.md`, `docs/4-architecture/features/010/spec.md`
- Related ADRs: ADR-0004 (Documentation & Aggregated Quality Gate Workflow), ADR-0005 (Operator Console and UI Contracts)
- Related issues / PRs: (to be linked from future Native Java API increments per protocol)

