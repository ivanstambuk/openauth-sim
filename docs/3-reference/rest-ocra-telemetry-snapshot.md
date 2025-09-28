# REST OCRA Telemetry Snapshot

This snapshot records representative log lines emitted by the OCRA evaluation endpoint during automated tests. Regenerate it with:

```
./gradlew :rest-api:test --tests io.openauth.sim.rest.OcraEvaluationEndpointTest --info
```

## Sample Output (2025-09-28)
```
2025-09-28T17:44:11.554+02:00  INFO 604833 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.evaluate status=success telemetryId=rest-ocra-ef2c742c-0613-43c8-ac22-eca2e6d4680c suite=OCRA-1:HOTP-SHA256-8:QA08-S064 hasSessionPayload=true hasClientChallenge=false hasServerChallenge=false hasPin=false hasTimestamp=false durationMillis=33 reasonCode=success sanitized=true
2025-09-28T17:44:11.969+02:00  WARN 604833 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.evaluate status=invalid telemetryId=rest-ocra-c6279562-1abd-4ff2-9ea0-df0914964377 suite=OCRA-1:HOTPT30SHA256-7:QN08 hasSessionPayload=false hasClientChallenge=false hasServerChallenge=false hasPin=false hasTimestamp=true durationMillis=3 reasonCode=timestamp_drift_exceeded sanitized=true reason=timestampHex is outside the permitted drift window
2025-09-28T17:44:11.992+02:00  WARN 604833 --- [    Test worker] io.openauth.sim.rest.ocra.telemetry      : event=rest.ocra.evaluate status=invalid telemetryId=rest-ocra-4441f6ba-eaf3-4368-a3a1-65ef4a272050 suite=OCRA-1:HOTP-SHA1-6:QA08-PSHA1 hasSessionPayload=false hasClientChallenge=false hasServerChallenge=false hasPin=true hasTimestamp=false durationMillis=3 reasonCode=pin_hash_mismatch sanitized=true reason=pinHashHex must match the suite hash format
```

Keep this snapshot in sync when telemetry fields change.
