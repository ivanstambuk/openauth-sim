# Reference (In Progress)

Auto-generated API, CLI, and schema references land here as surfaces come online.

## Available Artifacts
- `rest-openapi.json` – SpringDoc-generated contract for `/api/v1/ocra/evaluate`. Refresh via `OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests io.openauth.sim.rest.OpenApiSnapshotTest` before committing behaviour changes.
- `rest-ocra-telemetry-snapshot.md` – Sample telemetry lines emitted by the OCRA evaluation endpoint. Regenerate via `./gradlew :rest-api:test --tests io.openauth.sim.rest.OcraEvaluationEndpointTest --info`.

## Pending Artifacts
- `core` Javadoc (to be generated once APIs stabilise)
- Open standard specifications (FIDO2/WebAuthn, OATH/OCRA, EUDI wallet, EMV) via external links captured in future documentation updates.
