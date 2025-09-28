# REST OCRA Telemetry Snapshot

This snapshot records representative log lines emitted by the OCRA evaluation endpoint during automated tests. Regenerate it with:

```
./gradlew :rest-api:test --tests io.openauth.sim.rest.OcraEvaluationEndpointTest --info
```

## Sample Output (2025-09-28)
```
2025-09-28T16:36:21.088+02:00  INFO 566388 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.evaluate status=success telemetryId=rest-ocra-8859d14c-45df-4c6d-9a89-dd7adb15f9d8 suite=OCRA-1:HOTP-SHA256-8:QA08-S064 hasSessionPayload=true hasClientChallenge=false hasServerChallenge=false hasPin=false hasTimestamp=false durationMillis=34
2025-09-28T16:36:21.292+02:00  WARN 566388 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.evaluate status=invalid telemetryId=rest-ocra-1476d363-ff46-4c27-aaac-555d34b49dab suite=OCRA-1:HOTP-SHA256-8:QA08-S064 hasSessionPayload=false hasClientChallenge=false hasServerChallenge=false hasPin=false hasTimestamp=false durationMillis=1 reason=sessionInformation required for suite: OCRA-1:HOTP-SHA256-8:QA08-S064
```

Keep this snapshot in sync when telemetry fields change.
