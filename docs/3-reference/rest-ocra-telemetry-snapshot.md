# REST OCRA Telemetry Snapshot

This snapshot records representative log lines emitted by the OCRA evaluation endpoint during automated tests. Regenerate it with:

```
./gradlew :rest-api:test --tests io.openauth.sim.rest.OcraEvaluationEndpointTest --info
```

## Sample Output (2025-09-28)
```
2025-09-28T17:11:10.233+02:00  INFO 585627 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.evaluate status=success telemetryId=rest-ocra-4479b0b6-f539-41c6-9caf-4bc17ad3e3f2 suite=OCRA-1:HOTP-SHA256-8:QA08-S512 hasSessionPayload=true hasClientChallenge=false hasServerChallenge=false hasPin=false hasTimestamp=false durationMillis=4 reasonCode=success sanitized=true
2025-09-28T17:11:10.402+02:00  WARN 585627 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.evaluate status=invalid telemetryId=rest-ocra-a2b9319d-e2db-4e00-99aa-25dbfd5f066d suite=OCRA-1:HOTP-SHA1-6:C-QN08 hasSessionPayload=false hasClientChallenge=false hasServerChallenge=false hasPin=false hasTimestamp=false durationMillis=1 reasonCode=counter_required sanitized=true reason=counter is required for the requested suite
```

Keep this snapshot in sync when telemetry fields change.
