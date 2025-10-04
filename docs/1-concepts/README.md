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

### Operator Console Seeding Flow

- Switch the console to **Stored credential** mode to reveal the `Seed sample credentials` control; the button stays hidden for inline evaluations so the form remains uncluttered.
- Selecting the control invokes `POST /api/v1/ocra/credentials/seed`, which appends any missing canonical presets (matching the inline autofill list) without overwriting existing records. Re-running the action simply adds suites that are still absent.
- Successful invocations refresh the stored credential dropdown and emit `ocra.seed` telemetry with the number of credentials created; no secret material or existing entries are exposed.
- Operators working outside the UI can call the same REST endpoint directly; see the OpenAPI snapshot in `docs/3-reference/rest-openapi.json`/`.yaml` for the response contract.

### Validation & Telemetry

- All factory and validation helpers emit descriptive exceptions while redacting secret material.
- Structured debug-level telemetry is produced under the logger `io.openauth.sim.core.credentials.ocra.validation` with event name `ocra.validation.failure` when a validation step fails.
- Telemetry payload fields: `credentialName`, `suite`, `failureCode` (`CREATE_DESCRIPTOR`, `VALIDATE_CHALLENGE`, `VALIDATE_SESSION`, `VALIDATE_TIMESTAMP`), `messageId` (`OCRA-VAL-00x`), and optional `detail`. Secrets and challenge/session values are never logged.
- Operators can correlate telemetry with persistence state via the credential name; see Feature 001 specification for field-level requirements.

## Glossary

- **OCRA Descriptor** – Immutable record containing suite metadata, shared secret, optional counter/PIN/timestamp drift settings, and custom metadata.
- **Versioned Credential Record** – Persistence envelope (current schema v1) capturing credential data, timestamps, and namespaced attributes. MapDB migrations upgrade legacy schemas on load.
- **Structured Validation Telemetry** – Debug log events emitted when descriptor creation or auxiliary validations fail, designed for future observability pipelines without exposing sensitive material.
- **Persistence Maintenance Helper** – Opt-in MapDB helper that performs compaction and integrity checks synchronously, emitting `persistence.credential.maintenance` telemetry for observability.
- **Persistence Encryption** – Optional AES-GCM layer that encrypts credential secrets at rest using caller-supplied in-memory keys while preserving the `CredentialStore` API contract.

## References

- `docs/4-architecture/specs/feature-001-core-credential-domain.md`
- `docs/4-architecture/feature-plan-001-core-domain.md`
- `core/src/main/java/io/openauth/sim/core/credentials/ocra/OcraCredentialFactory.java`
- `core/src/main/java/io/openauth/sim/core/store/encryption/PersistenceEncryption.java`
- `docs/2-how-to/configure-persistence-profiles.md`
