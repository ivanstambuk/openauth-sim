# EUDIW OpenID4VP Telemetry Snapshot

Captured on 2025-11-15 after rerunning the targeted suites (`./gradlew --no-daemon :application:test :cli:test :rest-api:test`, `node --test rest-api/src/test/javascript/eudi/openid4vp/console.test.js`, `OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH=build/tmp/test-credentials.db ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.EudiwOperatorUiSeleniumTest"`). Use these frames to regression-test redaction rules and to confirm the refreshed `oid4vp.fixtures.ingested` signal stays sanitized.

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
event=oid4vp.response.failed sanitized=true telemetryId=ui-oid4vp-validate-1af0 requestId=OID4VP-REQ-00088 reason=invalid_scope trustedAuthorityRequested=etsi_tl:lotl-373
detail={"title":"Trusted Authority requirement not satisfied","violations":[{"field":"trustedAuthorities","message":"etsi_tl:lotl-373 missing"}]}
```
- Validation telemetry always carries `presentations=1` today. When multi-credential DCQL responses land, we will emit one event per credential to keep payloads small.
- UI/CLI prefixes (`ui-oid4vp-*`, `cli-oid4vp-*`) make it easy to identify the facade that triggered the telemetry frame.

## Fixture ingestion (application layer)
```
event=oid4vp.fixtures.ingested sanitized=true telemetryId=app-oid4vp-ingest-a81c source=conformance requestedCount=1 ingestedCount=1 provenanceSource="EU LOTL + Member-State Trusted Lists (DE, SI)" provenanceVersion=2025-11-15T13:11:59Z provenanceHash=sha256:cb0dfbf7a8df9d7ea1b36bce46dbfc48ee5c40e8e6c6f43bae48e165b5bc69e5 ingestId=2025-11-15T13:11:59Z-optionA lotlSequenceNumber=373 memberStates=["DE","SI"]
```
- `source` matches `FixtureDatasets.Source` (`synthetic` or `conformance`). LOTL metadata (`ingestId`, `lotlSequenceNumber`, `memberStates`) surfaces directly from the fixture provenance JSON so you can diff successive imports.
- `requestedCount` equals the number of presentation IDs supplied in the ingestion request, enabling quick diffing between ad-hoc refreshes and full imports; `ingestedCount` reflects how many stored presentations matched after filtering by source.

## Troubleshooting tips
- All frames above intentionally omit raw VP Tokens, KB-JWTs, disclosures, DeviceResponse blobs, and non-masked nonces. Hashes (`vpTokenHash`, `kbJwtHash`, disclosure hashes) only appear in verbose traces—the telemetry catalog should never include them verbatim.
- When new fields are added to any event family, update this snapshot and re-run `./gradlew --no-daemon :application:test :cli:test :rest-api:test` to regenerate reference output.
