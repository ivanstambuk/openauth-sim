# Feature 040 Tasks – EUDIW OpenID4VP Simulator

_Status: Draft_  
_Last updated: 2025-11-01_

- [ ] T3999 – Trusted list ingestion foundation (F-040-18/25): add ETSI TL/OpenID Federation fixture metadata, local snapshots, synthetic issuer/holder key stubs, stored presentation metadata, and failing resolver tests. Cmd: `./gradlew --no-daemon :core:test`.
- [ ] T4001 – Fixture scaffolding & seed setup (F-040-18/19/25/31, N-040-01): add synthetic PID fixtures for SD-JWT VC + mdoc, deterministic seed files, synthetic issuer/holder keys, friendly issuer labels for Trusted Authorities, stored presentation records, and failing smoke tests ensuring fixture availability. Cmd: `./gradlew --no-daemon :core:test`.
- [ ] T4002 – Authorization request red tests (F-040-01/02/03/04/05/14): add failing tests for DCQL enforcement, nonce/state determinism, response mode toggles, telemetry expectations. Cmd: `./gradlew --no-daemon :application:test`.
- [ ] T4003 – Authorization request implementation: satisfy T4002, implement builder, QR renderer, telemetry, HAIP signed-request toggle. Cmds: `./gradlew --no-daemon :application:test`, `./gradlew --no-daemon spotlessApply check`.
- [ ] T4004 – SD-JWT wallet tests (F-040-07/08/10/13/23): stage failing tests covering VP Token shape, disclosure hashing, KB-JWT generation, and inline credential inputs (preset vs manual + sample selector). Cmd: `./gradlew --no-daemon :core:test :application:test`.
- [ ] T4005 – SD-JWT wallet implementation: pass T4004 by implementing deterministic SD-JWT + KB-JWT path, wiring inline credential hydration, and telemetry. Cmds: `./gradlew --no-daemon :core:test :application:test`, `./gradlew --no-daemon spotlessApply check`.
- [ ] T4006 – mdoc wallet tests (F-040-09/10/17/23): add failing tests for DeviceResponse parsing, Claims Path Pointer mapping, inline DeviceResponse uploads, sample selector population, HAIP encryption hooks. Cmd: `./gradlew --no-daemon :core:test :application:test`.
- [ ] T4007 – mdoc wallet implementation: satisfy T4006 by loading DeviceResponse fixtures, hydrating inline uploads, enforcing COSE signature validation, plumbing HAIP encryption flag. Cmds: `./gradlew --no-daemon :core:test :application:test`, `./gradlew --no-daemon spotlessApply check`.
- [ ] T4008 – Trusted Authorities & error handling tests (F-040-11/12/21): add failing tests for Authority Key Identifier (`aki`) positive/negative matches and OID4VP error mapping. Cmd: `./gradlew --no-daemon :application:test`.
- [ ] T4009 – Trusted Authorities implementation: implement Authority Key Identifier evaluator (DCQL `aki`), error mapping, telemetry redaction updates. Cmds: `./gradlew --no-daemon :application:test :core:test`, `./gradlew --no-daemon spotlessApply check`.
- [ ] T4010 – Encryption path tests (F-040-04/20/20a, N-040-03): failing tests for `direct_post.jwt` round-trip, latency telemetry capture, and decryption/error scenarios returning `invalid_request`. Cmd: `./gradlew --no-daemon :application:test`.
- [ ] T4011 – Encryption implementation: integrate JWE encoder/decoder (pending dependency approval), telemetry, HAIP retry behaviour, and configuration flags. Cmds: `./gradlew --no-daemon :application:test`, `./gradlew --no-daemon spotlessApply check`.
- [ ] T4012 – Validation mode tests (F-040-22/23): add failing tests covering VP Token inline/stored selectors, inline DCQL JSON preview, VP Token paste/upload verification, positive acceptance, and error classification alignment. Cmd: `./gradlew --no-daemon :application:test`.
- [ ] T4013 – Validation mode implementation: satisfy T4012 by wiring application verification entry point, telemetry events, inline/stored selector handling, and shared error mapper. Cmds: `./gradlew --no-daemon :application:test`, `./gradlew --no-daemon spotlessApply check`.
- [ ] T4014 – Facade integration tests (F-040-15/16/17/22): stage failing MockMvc, CLI, and Selenium tests verifying Generate/Validate flows and updated UI. Cmds: `./gradlew --no-daemon :rest-api:test :cli:test :ui:test`.
- [ ] T4015 – Facade implementation: implement REST controllers/DTOs, CLI commands, UI updates (Generate + Validate) including stored-mode seeding actions, regenerate OpenAPI snapshot. Cmds: `OPENAPI_SNAPSHOT_WRITE=true ./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.OpenApiSnapshotTest"`, `./gradlew --no-daemon :rest-api:test :cli:test :ui:test`, `./gradlew --no-daemon spotlessApply check`.
- [ ] T4016 – Global verbose/trace harmonization (F-040-14/F-040-30): wire the EUDIW tab into the console-wide verbose toggle and trace dock, remove panel-level checkboxes, and extend Selenium parity tests. Cmds: `./gradlew --no-daemon :ui:test`.
- [ ] T4017 – Multi-presentation rendering (F-040-09/22a): add integration tests for multi-credential DCQL responses (UI + traces) and implement collapsible result sections with copy controls. Cmds: `./gradlew --no-daemon :ui:test :application:test`.
- [ ] T4018 – Deep-link & flag consolidation (F-040-29/F-040-30): ensure `protocol/tab/mode` query params hydrate Evaluate/Replay, align REST/CLI verbose flags, and update OpenAPI snapshot expectations. Cmds: `./gradlew --no-daemon :rest-api:test :cli:test`.
- [ ] T4019 – Fixture ingestion toggle tests (F-040-18, N-040-04): failing tests for synthetic vs conformance selection and provenance metadata. Cmd: `./gradlew --no-daemon :core:test :application:test`.
- [ ] T4020 – Fixture ingestion implementation: add loader abstraction, provenance recording, documentation hooks. Cmds: `./gradlew --no-daemon :core:test :application:test`, `./gradlew --no-daemon spotlessApply check`.
- [ ] T4021 – Documentation & telemetry verification (All): refresh roadmap, knowledge map, telemetry catalog, author `docs/2-how-to/use-eudiw-operator-ui.md`, `docs/2-how-to/use-eudiw-rest-operations.md`, `docs/2-how-to/use-eudiw-cli-operations.md`, and verify full build. Cmds: `./gradlew --no-daemon :application:test :cli:test :core:test :rest-api:test :ui:test`, `./gradlew --no-daemon spotlessApply check`.

### Deferred Follow-ups
- [ ] T40F1 – Same-device/DC-API exploration once prioritised.  
- [ ] T40F2 – OpenID4VCI issuance simulator alignment for end-to-end wallet journeys.  
- [ ] T40F3 – Trusted Authorities expansion (live TL updates, OpenID Federation resolution enhancements).
