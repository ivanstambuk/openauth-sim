# Feature 028 Tasks – IDE Warning Remediation

_Linked plan:_ `docs/4-architecture/features/028/plan.md`  
_Status:_ Complete  
_Last updated:_ 2025-10-30

☑ **T2801 – Governance sync**   (S28-01)
  ☑ Create spec/plan/tasks files and capture Option B clarification in the spec.  
  ☑ Update roadmap and current-session snapshot; resolve the maintenance open question entry.

☑ **T2802 – Application constructors cleanup**   (S28-02)
  ☑ Remove redundant `evaluationInstant` assignments in TOTP evaluation/replay command records.  
  ☑ Run `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.totp.*"`.

☑ **T2803 – Core/WebAuthn services assertions**   (S28-02)
  ☑ Strengthen `WebAuthnAttestationVerifier` and `WebAuthnReplayService` assertions; remove obsolete suppressions.  
  ☑ Run `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAttestationVerifierTest"`.  
  ☑ Run `./gradlew --no-daemon :application:test --tests "io.openauth.sim.application.fido2.WebAuthnPublicKeyDecoderTest"` and `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2ReplayEndpointTest"`.

☑ **T2804 – CLI/REST unit tests**   (S28-03)
  ☑ Update `TotpCliTest` and `Fido2AttestationEndpointTest` to leverage previously unused locals.  
  ☑ Run `./gradlew --no-daemon :cli:test --tests "io.openauth.sim.cli.TotpCliTest"` and `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.Fido2AttestationEndpointTest"`.

☑ **T2805 – Selenium assertions**   (S28-03)
  ☑ Convert unused Selenium locals/constants into explicit assertions across operator UI suites.  
  ☑ Run `./gradlew --no-daemon :rest-api:test --tests "io.openauth.sim.rest.ui.*"` as needed.

☑ **T2806 – Quality gate**   (S28-04)
  ☑ Execute `./gradlew --no-daemon spotlessApply check`.  
  ☑ Note residual warnings, if any, for follow-up in the roadmap.

☑ **T2807 – WebAuthn assertion DTO extraction**   (S28-02)
  ☑ Move `WebAuthnAssertionResponse` into its own `rest-api` source file and adjust usages to match the public `WebAuthnGeneratedAssertion` contract.  
  ☑ Run `./gradlew --no-daemon --no-configuration-cache :rest-api:compileJava` (after `clean`) to confirm the auxiliary-class warning is resolved.

☑ **T2808 – SpotBugs annotation export**   (S28-04)
  ☑ Promote `spotbugs-annotations` to the application module’s exported compile classpath (`compileOnlyApi`) and refresh dependency locks via targeted `--write-locks` runs.  
  ☑ Execute `./gradlew --no-daemon --no-configuration-cache :application:compileJava`, `./gradlew --no-daemon --write-locks :application:compileJava :application:compileTestJava :application:pmdTest`, `./gradlew --no-daemon --write-locks :rest-api:compileJava :rest-api:compileTestJava :cli:compileJava :cli:compileTestJava :ui:compileJava :ui:compileTestJava`, and finish with `./gradlew --no-daemon --write-locks spotlessApply check` (followed by a non-locking re-run) to ensure downstream warnings are clear.

☑ **T2809 – REST exception serialization warnings**   (S28-03)
  ☑ Mark REST exception `details`/`metadata` fields as `transient` so they comply with serialization guidelines.  
  ☑ Run `./gradlew --no-daemon :rest-api:compileJava` (optionally `:rest-api:test`) to confirm the warning clears.

☑ **T2810 – WebAuthn assertion lossy conversion warning**   (S28-02)
  ☑ Update `WebAuthnAssertionVerifierTest` to avoid implicit int-to-byte conversions during XOR.  
  ☑ Execute `./gradlew --no-daemon :core:test --tests "io.openauth.sim.core.fido2.WebAuthnAssertionVerifierTest"`.

☑ **T2811 – WebAuthn replay/HOTP telemetry diagnostics**   (S28-02)
  ☑ Drop the unused `telemetryCommand` variable in `WebAuthnAttestationReplayApplicationService` and confirm telemetry output remains unchanged.  
  ☑ Guard `WebAuthnEvaluationApplicationService` trace helpers against null dereference warnings by populating authenticator/client data only inside trace blocks.  
  ☑ Prevent HOTP matched counter auto-unboxing by supplying a safe fallback and assert the PS256 Selenium fixture to remove the unused local.  
  ☑ Run `./gradlew --no-daemon spotlessApply check`.
  
2025-10-30 – IDE verification complete; no further remediation items remain.
