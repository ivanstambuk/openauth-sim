# REST OCRA Telemetry Snapshot

This snapshot records representative log lines emitted by the OCRA evaluation endpoint during automated tests. Regenerate it with:

```
./gradlew :rest-api:test --tests io.openauth.sim.rest.OcraEvaluationEndpointTest --info
```

## Sample Output (2025-09-28)
```
2025-09-28T18:19:21.964+02:00  INFO 627151 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.evaluate status=success telemetryId=rest-ocra-34f19cd3-6a8f-44b6-b852-796382cf3f73 suite=OCRA-1:HOTP-SHA256-8:QA08-S064 hasCredentialReference=true hasSessionPayload=true hasClientChallenge=false hasServerChallenge=false hasPin=false hasTimestamp=false durationMillis=32 reasonCode=success sanitized=true
2025-09-28T18:19:22.429+02:00  WARN 627151 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.evaluate status=invalid telemetryId=rest-ocra-6aafcb02-e03e-4cff-a243-833ff36b59a5 suite=OCRA-1:HOTPT30SHA256-7:QN08 hasCredentialReference=false hasSessionPayload=false hasClientChallenge=false hasServerChallenge=false hasPin=false hasTimestamp=true durationMillis=3 reasonCode=timestamp_drift_exceeded sanitized=true reason=timestampHex is outside the permitted drift window
2025-09-28T18:19:22.488+02:00  WARN 627151 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.evaluate status=invalid telemetryId=rest-ocra-2a8aa3e5-29dc-4c67-ac7d-94b9b3f4abd0 suite=OCRA-1:HOTP-SHA256-8:QA08-S064 hasCredentialReference=true hasSessionPayload=true hasClientChallenge=false hasServerChallenge=false hasPin=false hasTimestamp=false durationMillis=0 reasonCode=credential_conflict sanitized=true reason=Provide either credentialId or sharedSecretHex, not both
```

Keep this snapshot in sync when telemetry fields change.
