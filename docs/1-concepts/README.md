# Concepts (Draft)

This catalogue introduces the abstractions that power the OpenAuth Simulator's credential domain. Content is incrementally expanded as each protocol package ships.

## OCRA Credential Overview

OATH Challenge-Response Algorithm (OCRA) credentials are the first protocol slice delivered under Feature 001. The core module treats them as immutable descriptors created via `OcraCredentialFactory` and persisted through versioned envelopes.

### Capability Matrix

| Attribute | Required | Notes |
|-----------|----------|-------|
| `name` | Yes | Globally unique identifier surfaced to operators and facades. |
| `ocraSuite` | Yes | RFC 6287 suite descriptor parsed into hash algorithm, response length, data inputs. |
| `sharedSecret` | Yes | Canonicalised into `SecretMaterial`; accepts RAW, HEX, or Base64 input encodings. |
| `counterValue` | Conditional | Required when the suite declares `C`; rejected otherwise. |
| `pinHash` | Conditional | Required when the suite declares `P{hash}`; stored as hex without leaking raw PINs. |
| `allowedTimestampDrift` | Optional | Overrides suite drift window when timestamp input is enabled. |
| `metadata` | Optional | Arbitrary key/value pairs returned on lookup but ignored during crypto operations. |

### Validation & Telemetry

- All factory and validation helpers emit descriptive exceptions while redacting secret material.
- Structured debug-level telemetry is produced under the logger `io.openauth.sim.core.credentials.ocra.validation` with event name `ocra.validation.failure` when a validation step fails.
- Telemetry payload fields: `credentialName`, `suite`, `failureCode` (`CREATE_DESCRIPTOR`, `VALIDATE_CHALLENGE`, `VALIDATE_SESSION`, `VALIDATE_TIMESTAMP`), `messageId` (`OCRA-VAL-00x`), and optional `detail`. Secrets and challenge/session values are never logged.
- Operators can correlate telemetry with persistence state via the credential name; see Feature 001 specification for field-level requirements.

## Glossary

- **OCRA Descriptor** – Immutable record containing suite metadata, shared secret, optional counter/PIN/timestamp drift settings, and custom metadata.
- **Versioned Credential Record** – Persistence envelope (current schema v1) capturing credential data, timestamps, and namespaced attributes. MapDB migrations upgrade legacy schemas on load.
- **Structured Validation Telemetry** – Debug log events emitted when descriptor creation or auxiliary validations fail, designed for future observability pipelines without exposing sensitive material.

## References

- `docs/4-architecture/specs/feature-001-core-credential-domain.md`
- `docs/4-architecture/feature-plan-001-core-domain.md`
- `core/src/main/java/io/openauth/sim/core/credentials/ocra/OcraCredentialFactory.java`
