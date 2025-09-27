# OCRA Credential Data Model (Draft)

_Status: Draft_
_Last updated: 2025-09-27_

This document captures the initial persistence/data-contract model for OCRA credentials inside the shared MapDB store. Future protocol packages should follow the same envelope pattern.

## Envelope Structure

All credential records are stored inside a versioned envelope to support schema evolution.

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `schemaVersion` | integer | Yes | Increment on breaking data model changes. Initial value: `1`. |
| `credentialType` | string | Yes | Discriminator for protocol package; use `OCRA` for this model. |
| `payload` | object | Yes | Protocol-specific data defined below. |

## OCRA Payload

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `name` | string | Yes | Globally unique credential identifier shared across all protocol types. Treated as natural key for lookups. |
| `ocraSuite` | string | Yes | Canonical `<Algorithm>:<CryptoFunction>:<DataInput>` value exactly as defined in RFC 6287. Drives validation and runtime input requirements. |
| `sharedSecretKey` | string | Yes | Hex-encoded OCRA secret `K`. Stored without separators; converted to bytes when computing responses. |
| `counterValue` | integer (64-bit) | Conditional | Present only when `ocraSuite` includes the `C` data input. Represents the current client-side counter; emulator updates this locally. |
| `pinHash` | string | Conditional | Present only when `ocraSuite` includes a `P{hash}` segment. Contains the hashed PIN value using the hash algorithm implied by the suite. |
| `customAttributes` | object (map<string,string>) | Optional | Arbitrary metadata exposed to clients via query/list operations but never fed into cryptographic calculations. Keys must be case-sensitive ASCII; values UTF-8 strings. |

## Validation Rules

1. `name` must be unique across the entire credential catalog; attempt to persist a duplicate must fail.
2. `ocraSuite` must match the RFC 6287 grammar; validation should parse and normalize the suite before persistence.
3. `sharedSecretKey` must decode to between 128 and 512 bits depending on `ocraSuite` algorithm requirements.
4. `counterValue` is mandatory when `C` is present and must fit within an unsigned 8-byte range.
5. `pinHash` is mandatory when the suite declares a hashed PIN and should already be processed with the specified hash algorithm (e.g., `SHA1`, `SHA256`).
6. `customAttributes` keys must not collide with reserved field names (`name`, `ocraSuite`, `sharedSecretKey`, `counterValue`, `pinHash`).

## Example Record

```json
{
  "schemaVersion": 1,
  "credentialType": "OCRA",
  "payload": {
    "name": "demo-ocra-soft-token",
    "ocraSuite": "OCRA-1:HOTP-SHA1-6:QN08-T1M",
    "sharedSecretKey": "3132333435363738393031323334353637383930",
    "counterValue": 0,
    "customAttributes": {
      "issuer": "DemoBank",
      "environment": "staging"
    }
  }
}
```

Add future protocol payload definitions alongside this document or fold them into a consolidated credential data model once additional suites are specified.
