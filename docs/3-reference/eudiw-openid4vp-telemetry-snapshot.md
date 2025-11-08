# EUDIW OpenID4VP Telemetry Snapshot

Captured on 2025-11-08 after running the REST + CLI suites (`./gradlew --no-daemon :application:test :cli:test :rest-api:test`). Use these frames to regression-test redaction rules and to confirm the new `oid4vp.fixtures.ingested` signal stays sanitized.

## Request creation (REST)
```
event=oid4vp.request.created sanitized=true telemetryId=rest-oid4vp-req-5f2c profile=HAIP responseMode=DIRECT_POST_JWT haipMode=true requestId=OID4VP-REQ-00042 trustedAuthorities=[aki:s9tIpP7qrS9=] nonceMasked=******3CF29 stateMasked=******1XRZ1 qrAscii=████…
```
- `trustedAuthorityMetadata` (omitted above) appears when the preset defines friendly labels (EU PID Issuer, ETSI TL, etc.).
- `nonceFull` / `stateFull` only surface when `verbose=true`.

## Wallet simulation (CLI)
```
event=oid4vp.wallet.responded sanitized=true telemetryId=cli-oid4vp-wallet-e7c1 requestId=OID4VP-REQ-00042 profile=HAIP responseMode=DIRECT_POST_JWT presentations=1 trustedAuthority.policy=aki:s9tIpP7qrS9=
```
- `trustedAuthorityRequested` echoes the CLI `--trusted-authority` flag, allowing you to correlate operator choices with evaluator decisions.

## Validation success vs failure (REST/UI/CLI)
```
event=oid4vp.response.validated sanitized=true telemetryId=rest-oid4vp-validate-93b1 requestId=OID4VP-REQ-00077 profile=HAIP responseMode=DIRECT_POST_JWT presentations=1 trustedAuthority.policy=aki:s9tIpP7qrS9= walletPreset=pid-haip-baseline
```
```
event=oid4vp.response.failed sanitized=true telemetryId=ui-oid4vp-validate-1af0 requestId=OID4VP-REQ-00088 reason=invalid_scope trustedAuthorityRequested=etsi_tl:EU-2025-09-TL
detail={"title":"Trusted Authority requirement not satisfied","violations":[{"field":"trustedAuthorities","message":"etsi_tl:EU-2025-09-TL missing"}]}
```
- Validation telemetry always carries `presentations=1` today. When multi-credential DCQL responses land, we will emit one event per credential to keep payloads small.
- UI/CLI prefixes (`ui-oid4vp-*`, `cli-oid4vp-*`) make it easy to identify the facade that triggered the telemetry frame.

## Fixture ingestion (application layer)
```
event=oid4vp.fixtures.ingested sanitized=true telemetryId=app-oid4vp-ingest-4c31 source=synthetic requestedCount=0 ingestedCount=1 provenanceVersion=2025-11-01 provenanceHash=sha256:synthetic-openid4vp-v1 requestedIds=[]
```
- `source` matches `FixtureDatasets.Source` (`synthetic` or `conformance`).
- `requestedCount` equals the number of presentation IDs supplied in the ingestion request, enabling quick diffing between ad-hoc refreshes and full imports.

## Troubleshooting tips
- All frames above intentionally omit raw VP Tokens, KB-JWTs, disclosures, DeviceResponse blobs, and non-masked nonces. Hashes (`vpTokenHash`, `kbJwtHash`, disclosure hashes) only appear in verbose traces—the telemetry catalog should never include them verbatim.
- When new fields are added to any event family, update this snapshot and re-run `./gradlew --no-daemon :application:test :cli:test :rest-api:test` to regenerate reference output.
