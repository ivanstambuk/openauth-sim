# Use the HOTP Operator UI

_Status: Draft_
_Last updated: 2025-10-05_

The operator console embedded in the REST API now provides HOTP evaluation tooling alongside the existing OCRA flows. This guide walks through evaluating stored credentials, running inline checks, and understanding telemetry/counter updates.

## Prerequisites
- Run the REST API (`./gradlew :rest-api:bootRun`) so the operator console is reachable at `http://localhost:8080/ui/console`.
- Ensure the simulator can access a MapDB credential store (defaults to `data/credentials.db`). Use the CLI to import HOTP credentials if none exist.
- Keep the CLI or REST endpoints available for seeding new credentials or clearing counters when needed.

## Evaluate a Stored HOTP Credential
1. Open `http://localhost:8080/ui/console?protocol=hotp` and select the **HOTP** tab if it is not already active.
2. The console fetches stored HOTP credentials from `/api/v1/hotp/credentials`. Choose an entry from the **Credential** dropdown; labels include hash algorithm and digit length for quick reference.
3. Enter the observed OTP in the **One-time password** field and select **Evaluate stored credential**.
4. Successful evaluations display a **match** status along with metadata (hash algorithm, digit length, previous/next counter, telemetry identifier). The MapDB counter advances automatically.
5. Validation errors (missing OTP, counter drift, credential not found) appear in the error panel with sanitized messaging; counters are not mutated when evaluation fails.

## Run Inline HOTP Checks
1. In the **Inline evaluation** card, provide the identifier (optional), shared secret (hex), hash algorithm, digit length, counter, and OTP.
2. Choose **Evaluate inline parameters** to call `/api/v1/hotp/evaluate/inline` without mutating stored credentials.
3. Result metadata indicates the credential source (`inline`) and echoes counter bounds so you can confirm drift handling before persisting the credential.

## Telemetry and Counter Hygiene
- All evaluations emit `rest.hotp.evaluate` telemetry via the shared `TelemetryContracts` adapters. Result panels surface the telemetry identifier for correlation with downstream logs.
- Stored evaluations include `previousCounter`/`nextCounter` values so operators can identify drift or replay attempts quickly. Inline evaluations report the submitted counter for audit.
- UI requests reuse the console CSRF token; if a 403 occurs, refresh the page to mint a new session token before retrying.
- Seed or import additional HOTP credentials with the CLI (`./gradlew :cli:run --args='hotp import …'`) to keep the directory populated for operator drills.

Keep this document updated as HOTP issuance or additional operator affordances land.
