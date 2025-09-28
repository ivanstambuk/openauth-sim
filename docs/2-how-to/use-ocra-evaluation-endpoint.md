# How to Evaluate OCRA Challenges via REST

This guide walks operators through invoking the `/api/v1/ocra/evaluate` endpoint exposed by the REST facade. It covers runtime prerequisites, how to inspect the generated OpenAPI contract, and sample requests that mirror the RFC 6287 fixtures shipped with the simulator.

## Prerequisites
- Java 17 JDK available on the path (the project constitution requires `JAVA_HOME` to point at it before running Gradle).
- Build the project once to warm up caches: `./gradlew :rest-api:classes`.
- From the repository root, start the REST facade on port 8080:
  ```bash
  ./gradlew :rest-api:bootRun
  ```
  The application logs will indicate when Spring Boot has finished initialising. Leave the process running while issuing requests.

## Inspect the OpenAPI Contract
SpringDoc exposes two helpful endpoints once the service is running:

- Raw JSON specification: `http://localhost:8080/v3/api-docs`
- Interactive Swagger UI: `http://localhost:8080/swagger-ui/index.html`

A checked-in snapshot lives at `docs/3-reference/rest-openapi.json`. Keep this file in sync by running
```
OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests io.openauth.sim.rest.OpenApiSnapshotTest
```
whenever controller contracts change.

## Execute a Sample Evaluation
Send a minimal POST request that supplies the RFC S064 session payload:

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

## Error Handling
If payload validation fails (for example, omitting the `sessionHex` field or supplying non-hex characters), the service returns HTTP 400 with a redacted error body:

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

The `reasonCode` pinpoints why validation failed (`session_required`, `counter_required`, `timestamp_drift_exceeded`, `pin_hash_mismatch`, etc.), while `sanitized=true` confirms the payload was scrubbed before logging. All telemetry identifiers are synthetic; secrets never appear in responses or logs. Use the telemetry ID to trace execution in downstream monitoring systems and correlate with the structured log lines described in `docs/3-reference/rest-ocra-telemetry-snapshot.md`.

## Reference Stored Credentials Instead of Inline Secrets
Inbound requests can omit `sharedSecretHex` and supply `credentialId` instead. When provided, the REST facade fetches the descriptor from the persistence layer and reuses the stored secret/counter/PIN metadata while still allowing you to send per-request inputs (challenge, session payload, timestamp, etc.).

```bash
curl -s \
  -H "Content-Type: application/json" \
  -d '{
        "credentialId": "rest-ocra-stored",
        "suite": "OCRA-1:HOTP-SHA256-8:QA08-S064",
        "challenge": "SESSION01",
        "sessionHex": "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
      }' \
  http://localhost:8080/api/v1/ocra/evaluate | jq
```

Responses look identical to the inline-secret mode, but telemetry now flags `hasCredentialReference=true`. If the credential cannot be resolved, the API returns HTTP 400 with `reasonCode=credential_not_found`. Supplying both `credentialId` and `sharedSecretHex` is rejected with `reasonCode=credential_conflict` to avoid ambiguity.

## Next Steps
- For additional suites (S128/S256/S512) adjust `sessionHex` to the appropriate fixture.
- Track contract changes by updating both the OpenAPI snapshot and this how-to whenever request/response DTOs evolve.
