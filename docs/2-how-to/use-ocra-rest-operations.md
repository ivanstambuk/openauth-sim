# How to Operate the OCRA REST API

This guide teaches operators how to interact with every OCRA REST endpoint exposed by the OpenAuth Simulator. You will learn how to explore the OpenAPI contract, list stored credentials, perform OTP evaluations (inline and stored-credential modes), and navigate the Swagger UI.

## Prerequisites
- Java 17 JDK configured (`JAVA_HOME` must point to it).
- Repository dependencies installed via Gradle.
- Default MapDB database (`data/credentials.db`) populated with any credentials you plan to reference. The REST service delegates persistence to `CredentialStoreFactory`, so the same file is shared with CLI/UI. If you rely on a legacy file such as `data/ocra-credentials.db`, either rename it or set `--openauth.sim.persistence.database-path` explicitly before launching. Use the CLI guide if you need to import fixtures.

## 1. Start the REST Service
From the repository root:
```bash
./gradlew :rest-api:bootRun
```
The service exposes endpoints on `http://localhost:8080` and wires OCRA orchestration through the shared application services (no direct domain wiring required).

### Inspect the OpenAPI Contract
Once the service is running:
- JSON: `http://localhost:8080/v3/api-docs`
- YAML: `http://localhost:8080/v3/api-docs.yaml`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

Checked-in snapshots live at `docs/3-reference/rest-openapi.json` and `docs/3-reference/rest-openapi.yaml`. Regenerate them after contract changes:
```bash
OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests io.openauth.sim.rest.OpenApiSnapshotTest
```

## 2. Discover Stored Credentials (`GET /api/v1/ocra/credentials`)
Use this read-only endpoint to fetch sanitized credential summaries for dropdowns or UI clients.
```bash
curl -s http://localhost:8080/api/v1/ocra/credentials | jq
```
Example response:
```json
[
  {
    "id": "operator-demo",
    "label": "operator-demo (OCRA-1:HOTP-SHA256-8:QA08-S064)",
    "suite": "OCRA-1:HOTP-SHA256-8:QA08-S064"
  }
]
```
If the persistence layer is disabled, the endpoint returns an empty array.

## 3. Evaluate OTPs (`POST /api/v1/ocra/evaluate`)
Send JSON payloads to compute OCRA responses. Two modes are available.

### 3.1 Inline Mode
Provide the suite + secret directly:
```bash
curl -s \
  -H "Content-Type: application/json" \
  -d '{
        "suite": "OCRA-1:HOTP-SHA256-8:QA08-S064",
        "sharedSecretHex": "3132333435363738393031323334353637383930313233343536373839303132",
        "challenge": "SESSION01",
        "sessionHex": "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
      }' \
  http://localhost:8080/api/v1/ocra/evaluate | jq
```
Expected response:
```json
{
  "otp": "17477202",
  "suite": "OCRA-1:HOTP-SHA256-8:QA08-S064",
  "telemetryId": "rest-ocra-<uuid>"
}
```

### 3.2 Stored Credential Mode
Reference a credential imported via the CLI (see Section 2).
```bash
curl -s \
  -H "Content-Type: application/json" \
  -d '{
        "credentialId": "operator-demo",
        "suite": "OCRA-1:HOTP-SHA256-8:QA08-S064",
        "challenge": "SESSION01",
        "sessionHex": "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
      }' \
  http://localhost:8080/api/v1/ocra/evaluate | jq
```
Responses mirror inline mode but add telemetry context (`hasCredentialReference=true` in logs).

### 3.3 Error Handling
When validation fails (missing parameters, ambiguous input), the API returns HTTP 400 with sanitized details:
```json
{
  "error": "invalid_input",
  "message": "sessionHex is required for the requested suite",
  "details": {
    "telemetryId": "rest-ocra-<uuid>",
    "status": "invalid",
    "suite": "OCRA-1:HOTP-SHA256-8:QA08-S064",
    "field": "sessionHex",
    "reasonCode": "session_required",
    "sanitized": "true"
  }
}
```
Use the telemetry ID to correlate with structured logs. Server errors (HTTP 500) return `error=internal_error` without leaking secrets.

## 4. Verify Historical OTPs (POST /api/v1/ocra/verify)
Use this endpoint to replay operator-supplied OTPs against either stored or inline credentials. The service never mutates counters, so repeated calls are safe when investigating incidents.

### 4.1 Stored credential mode
Provide the previously issued OTP plus the full OCRA context used when it was generated.
```bash
curl -s \
  -H "Content-Type: application/json" \
  -d '{
        "otp": "17477202",
        "credentialId": "operator-demo",
        "context": {
          "challenge": "SESSION01",
          "sessionHex": "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
        }
      }' \
  http://localhost:8080/api/v1/ocra/verify | jq
```
`status="match"` confirms the OTP is legitimate.

### 4.2 Inline secret mode
Use this when the credential is not stored in MapDB.
```bash
curl -s \
  -H "Content-Type: application/json" \
  -d '{
        "otp": "17477202",
        "inlineCredential": {
          "suite": "OCRA-1:HOTP-SHA256-8:QA08-S064",
          "sharedSecretHex": "3132333435363738393031323334353637383930313233343536373839303132"
        },
        "context": {
          "challenge": "SESSION01",
          "sessionHex": "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
        }
      }' \
  http://localhost:8080/api/v1/ocra/verify | jq
```
The response mirrors the stored path but reports `credentialSource="inline"` in telemetry.

### 4.3 Interpreting responses
- `status=match` / `reasonCode=match` – OTP replay succeeded (`200 OK`).
- `status=mismatch` / `reasonCode=strict_mismatch` – the OTP or context differs from the recorded values (`200 OK`).
- `status=invalid` / `reasonCode=validation_failure` – request failed validation (`422 Unprocessable Entity`).
- `reasonCode=credential_not_found` – MapDB does not contain the requested ID (`404 Not Found`).

Each response contains `metadata.durationMillis` so you can confirm latency stays under 150 ms (stored) or 200 ms (inline). Structured telemetry is emitted through `TelemetryContracts`, keeping logs sanitised without bespoke adapters.

### 4.4 Audit telemetry
Verification emits `event=rest.ocra.verify` with hashed payloads (`otpHash`, `contextFingerprint`) and an explicit `credentialSource`. Capture the `telemetryId`, `reasonCode`, and `outcome` fields when filing audit reports. Sample log lines live in `docs/3-reference/rest-ocra-telemetry-snapshot.md`.

### 4.5 Failure drills
Issue a request with an intentionally altered counter, timestamp, or OTP to rehearse `strict_mismatch` handling. Terminate requests at the operator UI or CLI once the telemetry confirms the mismatch path is logged.


## 5. Navigate the Operator UI (`GET /ui/ocra`)
The REST service also hosts an operator UI that consumes the same endpoints via asynchronous fetch calls. Access it at `http://localhost:8080/ui/ocra` to validate JSON requests interactively before automating them in your tooling.

### 5.1 Replay telemetry
The replay screen issues two network calls: a POST to `/api/v1/ocra/verify` and a follow-up POST to `/ui/ocra/replay/telemetry` once a response arrives. The telemetry payload mirrors the REST metadata (`mode`, `credentialSource`, `outcome`, `contextFingerprint`) and flows through `TelemetryContracts.ocraVerificationAdapter` with `event=ui.ocra.replay`. Example log line:
```
2025-10-03T11:42:09.117Z INFO io.openauth.sim.rest.ui.telemetry : event=ui.ocra.replay status=match telemetryId=ui-replay-793aac origin=ui uiView=replay mode=stored credentialSource=stored outcome=match contextFingerprint=d8o6y8HgxWfaJQ2a1zAifg sanitized=true
```
Validation failures produce `status=invalid` (reason code defaults to `validation_error`) and include the offending field in the REST response. Network failures emit `status=error` / `reasonCode=unexpected_error` with `sanitized=false`, signalling operators to retry the REST request.

## 6. Replay and Audit Workflows
- Reuse the same payload with updated challenges to rehearse replay handling. Counter/timestamp suites enforce their own drift rules and surface `reasonCode` hints when inputs are invalid.
- Collect telemetry IDs from responses or application logs to attach to support tickets and monitoring dashboards.
- Pair REST evaluations with database maintenance by running CLI commands (`maintenance compact`, `maintenance verify`) between test cycles.

## Related Resources
- [How to Operate the OCRA CLI](use-ocra-cli-operations.md) for seeding and maintaining credential stores.
- [How to Drive OCRA Evaluations from Java Applications](use-ocra-from-java.md) for embedding these REST calls inside your own code.
- REST OpenAPI snapshots at `docs/3-reference/rest-openapi.json` / `.yaml` for offline inspection.
