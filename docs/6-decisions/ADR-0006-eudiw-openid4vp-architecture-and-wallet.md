# ADR-0006: EUDIW OpenID4VP Architecture and Wallet Partitioning

- **Status:** Accepted
- **Date:** 2025-11-15
- **Related features/specs:** Feature 006 ([docs/4-architecture/features/006/spec.md](docs/4-architecture/features/006/spec.md)), Feature 007 ([docs/4-architecture/features/007/spec.md](docs/4-architecture/features/007/spec.md)), Feature 008 ([docs/4-architecture/features/008/spec.md](docs/4-architecture/features/008/spec.md))
- **Related open questions:** (none currently)

## Context

The EUDIW/OpenID4VP work in OpenAuth Simulator spans multiple capabilities:
- HAIP-aligned verifier flows (OpenID4VP remote verifier, DCQL evaluation, Trusted Authority handling).
- Shared fixtures, Trusted Authority metadata, and test vectors.
- A planned SIOPv2 wallet simulator for cross-device presentations using SD-JWT VC and mdoc DeviceResponse payloads.

To keep the EUDIW stack maintainable and HAIP-aligned, we need a clear architectural partition between verifier, fixtures,
and wallet responsibilities while ensuring they share a single set of fixtures and Trusted Authority metadata.

## Decision

Adopt the following partitioning and shared-asset model for EUDIW/OpenID4VP:
- Feature 006 – Verifier:
  - Owns the OpenID4VP/HAIP remote verifier flows, DCQL evaluators, and validation endpoints consumed by CLI/REST/UI.
  - Defines how Trusted Authorities are loaded (e.g., ETSI TL/OpenID Federation), filtered, and surfaced in telemetry.
  - Remains the source of truth for presentation validation rules and error semantics.
- Feature 007 – Fixtures and Trusted Authorities:
  - Owns EUDIW test vectors, SD-JWT/mdoc fixtures, and Trusted Authority snapshots shared across verifier and wallet flows.
  - Provides reusable fixture bundles under ``docs/test-vectors/eudiw`/**` and records provenance metadata.
- Feature 008 – Wallet simulator:
  - Owns the SIOPv2 wallet simulator for cross-device OpenID4VP flows, focusing on consent UX, presentation composition, and
    telemetry; implementation is placeholder as of 2025-11-15 but this feature remains the home for wallet requirements.
  - Shares fixtures and Trusted Authority metadata with Features 006/007 rather than duplicating them.
  - Must remain HAIP-aligned and reuse verifier endpoints for presentation validation.

Legacy Feature 008 (OCRA quality automation) artefacts are preserved under `docs/4-architecture/features/new-010/legacy/008/`
for historical reference; Feature 010 is now the canonical owner for documentation/quality automation.

## Consequences

### Positive
- Clear separation of concerns between verifier (006), fixtures/Trusted Authorities (007), and wallet simulator (008).
- Single shared fixture and Trusted Authority catalogue avoids duplication and keeps HAIP alignment in one place.
- Feature 008 can evolve wallet UX/flows without redefining verifier rules or duplicating test vectors.

### Negative
- Changes to EUDIW fixtures or Trusted Authorities must consider both verifier and wallet consumers, increasing coordination.
- Placeholder status for Feature 008 means its spec/plan/tasks describe intended behaviour before implementation; future
  iterations must keep this ADR aligned as the wallet fills in.

## Alternatives Considered

- **A – Single EUDIW feature for verifier + wallet**
  - Pros: Fewer feature artefacts to manage.
  - Cons: Harder to track verifier vs wallet scope; complicates planning and drift gates; mixes responsibilities.
- **B – Per-protocol features without a shared fixtures/TAs feature**
  - Pros: Local autonomy for each feature.
  - Cons: Encourages duplicated fixtures and diverging Trusted Authority handling; increases maintenance risk.
- **C – Verifier, fixtures, and wallet split across Features 006/007/008 (chosen)**
  - Pros: Keeps responsibilities focused while still sharing assets; aligns with roadmap entries and the current governance/spec model.

## Security / Privacy Impact

- Centralising fixtures and Trusted Authority metadata reduces the risk of inconsistent enforcement between verifier and wallet.
- Wallet simulator must honour the same redaction/telemetry rules as the verifier; shared `TelemetryContracts` adapters and
  fixtures make this easier to verify.

## Operational Impact

- Operators and maintainers must:
  - Treat Feature 006 as the source for verifier behaviour and validation rules.
  - Update fixtures/Trusted Authorities under Feature 007 when HAIP/OpenID4VP requirements change and ensure wallet/verifier
    tests stay in sync.
  - Use Feature 008 spec/plan/tasks when scoping wallet work, reusing existing fixtures and verifier endpoints instead of
    inventing separate data sets.

## Links

- Related spec sections: [docs/4-architecture/features/006/spec.md](docs/4-architecture/features/006/spec.md), [docs/4-architecture/features/007/spec.md](docs/4-architecture/features/007/spec.md),
  `[docs/4-architecture/features/008/spec.md](docs/4-architecture/features/008/spec.md)#overview`, `#goals`
- Related ADRs: ADR-0004 (Documentation & Aggregated Quality Gate Workflow), ADR-0005 (Operator Console Layout and Shared UI Contracts)
- Related issues / PRs: (to be linked from future EUDIW wallet/verifier updates)
