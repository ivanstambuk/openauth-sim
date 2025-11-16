# Reference (In Progress)

Auto-generated API, CLI, and schema references land here as surfaces come online.

## Available Artifacts
- `rest-openapi.json` – SpringDoc-generated contract for `/api/v1/ocra/evaluate`. Refresh via `OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests io.openauth.sim.rest.OpenApiSnapshotTest` before committing behaviour changes.
- `rest-ocra-telemetry-snapshot.md` – Sample telemetry lines emitted by the OCRA evaluation endpoint. Regenerate via `./gradlew :rest-api:test --tests io.openauth.sim.rest.OcraEvaluationEndpointTest --info`.
- `cli-ocra-telemetry-snapshot.md` – CLI telemetry reference captured during the Picocli OCRA smoke tests.
- `eudiw-openid4vp-telemetry-snapshot.md` – New telemetry catalog for Feature 040 covering `oid4vp.request.created`, `oid4vp.wallet.responded`, `oid4vp.response.*`, and `oid4vp.fixtures.ingested`. Refresh after major simulator or ingestion changes.
- `external-dependencies-by-facade-and-scenario.md` – Manually maintained matrix (Feature 010 FR-010-11) mapping, per protocol/facade/flow/credential source, which major external dependencies (MapDB/Caffeine, Spring Boot/Thymeleaf/Springdoc, Picocli, etc.) are exercised in runtime flows; use it to understand the stack that backs a given Evaluate/Replay scenario even when deploying a single fat JAR.

## Pending Artifacts
- Native Java API Javadoc index under `docs/3-reference/native-java-api/` (curated index still pending; generate full Javadoc with `./gradlew --no-daemon :application:nativeJavaApiJavadoc`, which runs `:core:javadoc` and `:application:javadoc` and writes HTML to `core/build/docs/javadoc` and `application/build/docs/javadoc`).
- Open standard specifications (FIDO2/WebAuthn, OATH/OCRA, EUDI wallet, EMV) via external links captured in future documentation updates.
