# REST OCRA Telemetry Snapshot

This snapshot records representative log lines emitted by the OCRA evaluation and verification endpoints during automated tests. Regenerate it with:

```
OPENAPI_SNAPSHOT_WRITE=true ./gradlew :rest-api:test --tests io.openauth.sim.rest.ocra.OcraVerificationServiceTest --info
./gradlew :rest-api:test --tests io.openauth.sim.rest.OcraEvaluationEndpointTest --info
```

## Sample Output – Evaluation (2025-09-28)
```
2025-09-28T18:19:21.964+02:00  INFO 627151 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.evaluate status=success telemetryId=rest-ocra-34f19cd3-6a8f-44b6-b852-796382cf3f73 suite=OCRA-1:HOTP-SHA256-8:QA08-S064 hasCredentialReference=true hasSessionPayload=true hasClientChallenge=false hasServerChallenge=false hasPin=false hasTimestamp=false durationMillis=32 reasonCode=success sanitized=true
2025-09-28T18:19:22.429+02:00  WARN 627151 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.evaluate status=invalid telemetryId=rest-ocra-6aafcb02-e03e-4cff-a243-833ff36b59a5 suite=OCRA-1:HOTPT30SHA256-7:QN08 hasCredentialReference=false hasSessionPayload=false hasClientChallenge=false hasServerChallenge=false hasPin=false hasTimestamp=true durationMillis=3 reasonCode=timestamp_drift_exceeded sanitized=true reason=timestampHex is outside the permitted drift window
2025-09-28T18:19:22.488+02:00  WARN 627151 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.evaluate status=invalid telemetryId=rest-ocra-2a8aa3e5-29dc-4c67-ac7d-94b9b3f4abd0 suite=OCRA-1:HOTP-SHA256-8:QA08-S064 hasCredentialReference=true hasSessionPayload=true hasClientChallenge=false hasServerChallenge=false hasPin=false hasTimestamp=false durationMillis=0 reasonCode=credential_conflict sanitized=true reason=Provide either credentialId or sharedSecretHex, not both
```

Keep this snapshot in sync when telemetry fields change.

## Sample Output – Verification (2025-10-01)
```
2025-10-01T09:42:18.112+02:00  INFO 720331 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.verify status=match outcome=match reasonCode=match telemetryId=rest-ocra-verify-6f6ebf86-0f2b-4dfa-9556-0c7e945c2f3f credentialSource=stored credentialId=inline-test otpHash=v3jAL0y3TQ1uDkL9DSN3rA contextFingerprint=d8o6y8HgxWfaJQ2a1zAifg sanitized=true durationMillis=8 httpStatus=200 requestId=req-inline-1 clientId=client-77 operator=operator-a
2025-10-01T09:42:18.214+02:00  INFO 720331 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.verify status=mismatch outcome=mismatch reasonCode=strict_mismatch telemetryId=rest-ocra-verify-4345ec88-9a75-4c8f-96f6-66f76a44b706 credentialSource=inline credentialId=inline-test otpHash=UNK5dxCwMBxQn1W5wI7F8Q contextFingerprint=d8o6y8HgxWfaJQ2a1zAifg sanitized=true durationMillis=5 httpStatus=200 requestId=req-inline-2 clientId=unspecified operator=anonymous
2025-10-01T09:42:18.327+02:00  WARN 720331 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.verify status=invalid outcome=invalid reasonCode=validation_failure telemetryId=rest-ocra-verify-31c2cbf3-0195-4c6d-b4ae-7aaec7f9bb3c credentialSource=inline credentialId=unspecified otpHash=4QrcO7~s;? contextFingerprint=unavailable sanitized=true durationMillis=1 httpStatus=422 requestId=req-inline-3 clientId=unspecified operator=anonymous reason=Verification inputs failed validation
```
