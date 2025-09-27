# Overview

OpenAuth Simulator emulates credential issuance, storage, and verification flows used by contemporary authentication standards (FIDO2/WebAuthn, OATH/OCRA, EUDI wallet credentials, EMV card authentication, etc.).

- **Audience:** security engineers, QA analysts, and AI agents experimenting with authentication scenarios without touching production infrastructure.
- **Scope (2025-09-27):** persistence API in `core` module with MapDB + Caffeine cache; CLI/REST/UI modules are stubs.
- **Out of scope:** production SLAs, multi-tenant controls, hardware attestation, long-term backwards compatibility.

See [docs/6-decisions/ADR-0001-core-storage.md](../6-decisions/ADR-0001-core-storage.md) for the storage choice rationale.
