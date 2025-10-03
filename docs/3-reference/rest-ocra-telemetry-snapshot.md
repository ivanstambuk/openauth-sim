# REST OCRA Telemetry Snapshot

This snapshot records representative log lines emitted by the OCRA evaluation and verification endpoints through the shared `TelemetryContracts` adapter during automated tests. Regenerate it with:

```
OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests io.openauth.sim.rest.ocra.OcraVerificationServiceTest --info
./gradlew :rest-api:test --tests io.openauth.sim.rest.OcraEvaluationEndpointTest --info
```

## Sample Output – Evaluation (2025-10-02)
```
2025-10-02T09:17:11.204+02:00  INFO 821640 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.evaluate status=success telemetryId=rest-ocra-1af4a8d2-6b35-4d94-bab3-fb37cb7959fd suite=OCRA-1:HOTP-SHA256-8:QA08-S064 hasCredentialReference=true hasSessionPayload=true hasClientChallenge=false hasServerChallenge=false hasPin=false hasTimestamp=false durationMillis=28 reasonCode=success sanitized=true
2025-10-02T09:17:11.632+02:00  WARN 821640 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.evaluate status=invalid telemetryId=rest-ocra-7e9c0417-932f-454d-8efa-d2e6f6e09aeb suite=OCRA-1:HOTPT30SHA256-7:QN08 hasCredentialReference=false hasSessionPayload=false hasClientChallenge=false hasServerChallenge=false hasPin=false hasTimestamp=true durationMillis=4 reasonCode=timestamp_drift_exceeded sanitized=true reason=timestampHex is outside the permitted drift window
2025-10-02T09:17:11.701+02:00  WARN 821640 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.evaluate status=invalid telemetryId=rest-ocra-d9dfd90a-fc88-4ad6-a1d1-725a41b8899f suite=OCRA-1:HOTP-SHA256-8:QA08-S064 hasCredentialReference=true hasSessionPayload=true hasClientChallenge=false hasServerChallenge=false hasPin=false hasTimestamp=false durationMillis=1 reasonCode=credential_conflict sanitized=true reason=Provide either credentialId or sharedSecretHex, not both
```

Keep this snapshot in sync when telemetry fields change.

## Sample Output – Operator UI Replay (2025-10-03)
```
2025-10-03T11:44:28.412+02:00  INFO 821640 --- [nio-8080-exec-4] io.openauth.sim.rest.ui.telemetry       : event=ui.ocra.replay status=match telemetryId=ui-replay-35f8fbb7 origin=ui uiView=replay mode=stored credentialSource=stored outcome=match contextFingerprint=d8o6y8HgxWfaJQ2a1zAifg sanitized=true
2025-10-03T11:44:29.006+02:00  WARN 821640 --- [nio-8080-exec-5] io.openauth.sim.rest.ui.telemetry       : event=ui.ocra.replay status=invalid telemetryId=ui-replay-f03bd90a origin=ui uiView=replay mode=inline credentialSource=inline outcome=invalid contextFingerprint=unavailable sanitized=true reasonCode=validation_error reason=Replay payload invalid
```

## Sample Output – Verification (2025-10-02)
```
2025-10-02T09:19:03.147+02:00  INFO 821640 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.verify status=match outcome=match reasonCode=match telemetryId=rest-ocra-verify-b8758708-1e33-4b2a-a23e-4b37a6d5db67 credentialSource=stored credentialId=inline-test otpHash=v3jAL0y3TQ1uDkL9DSN3rA contextFingerprint=d8o6y8HgxWfaJQ2a1zAifg sanitized=true durationMillis=7 httpStatus=200 requestId=req-inline-1 clientId=client-77 operator=operator-a
2025-10-02T09:19:03.246+02:00  INFO 821640 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.verify status=mismatch outcome=mismatch reasonCode=strict_mismatch telemetryId=rest-ocra-verify-44bb2b6e-7a2f-4eb1-9699-36c2946d0862 credentialSource=inline credentialId=inline-test otpHash=UNK5dxCwMBxQn1W5wI7F8Q contextFingerprint=d8o6y8HgxWfaJQ2a1zAifg sanitized=true durationMillis=5 httpStatus=200 requestId=req-inline-2 clientId=unspecified operator=anonymous
2025-10-02T09:19:03.352+02:00  WARN 821640 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.verify status=invalid outcome=invalid reasonCode=validation_failure telemetryId=rest-ocra-verify-b8f7ad77-5245-4c1c-879b-d4bebd74a7dc credentialSource=inline credentialId=unspecified otpHash=4QrcO7~s;? contextFingerprint=unavailable sanitized=true durationMillis=1 httpStatus=422 requestId=req-inline-3 clientId=unspecified operator=anonymous reason=Verification inputs failed validation
```
