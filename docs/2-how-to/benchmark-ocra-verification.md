# Benchmark OCRA Verification Latency

Use this runbook to capture the performance measurements required by ORV-NFR-002 (stored P95 ≤150 ms, inline P95 ≤200 ms). The benchmark harness lives in the `core` module and replays verification scenarios against curated RFC 6287 vectors.

## Prerequisites
- Java 17 JDK available (`java -version` should report 17.x).
- Gradle wrapper cached locally (`./gradlew --version`).
- Repository dependencies compiled once (`./gradlew :core:classes`).
- Hardware details captured for traceability (run `uname -a` and `java -version`).
- Optional: set `WSL_DISTRO_NAME` or similar environment metadata if running under WSL or containers.

Record these details alongside the benchmark results so auditors understand the execution environment.

## 1. Enable Benchmark Mode
The benchmark tests are skipped by default. Opt in by setting either system property `-Dio.openauth.sim.benchmark=true` or environment variable `IO_OPENAUTH_SIM_BENCHMARK=true`.

Example using the environment variable:
```bash
IO_OPENAUTH_SIM_BENCHMARK=true ./gradlew :core:test \
  --tests io.openauth.sim.core.credentials.ocra.OcraReplayVerifierBenchmark \
  --rerun-tasks --info
```
- `--rerun-tasks` ensures the benchmark executes even if previous test outputs exist.
- `--info` surfaces the structured `INFO` log lines with percentile data.

## 2. Understand the Output
The harness logs one line per mode (`stored`, `inline`). Example:
```
ocra-replay.stored warmup=2000 measured=20000 totalMs=543.671 throughputOpsPerSec=36786.97 p50Ms=0.018 p90Ms=0.043 p95Ms=0.060 p99Ms=0.142 maxMs=2.698
ocra-replay.inline warmup=2000 measured=20000 totalMs=309.811 throughputOpsPerSec=64555.41 p50Ms=0.012 p90Ms=0.020 p95Ms=0.024 p99Ms=0.047 maxMs=2.389
```
Interpretation:
- `warmup` / `measured` – number of operations used to prime the JVM vs recorded for metrics.
- `p95Ms` – critical value for ORV-NFR-002. Verify stored ≤150 ms and inline ≤200 ms.
- `maxMs` – helpful for spotting outliers even if P95 passes.
- `throughputOpsPerSec` – supplementary signal for capacity planning.

Because the harness filters out any vector that fails to replay (`strict_mismatch`, validation errors), all samples contribute to latency metrics.

## 3. Capture and Store Results
1. Copy the two log lines into `docs/4-architecture/features/009/plan.md` (or the active feature plan) with:
   - Execution date/time.
   - Environment summary (OS, CPU, Java vendor/version).
   - Confirmation that thresholds were met.
2. If rerunning later on different hardware, add a new bullet with the fresh details instead of overwriting the previous entry.
3. Keep supporting command output (optional) at `core/build/test-results/test/TEST-io.openauth.sim.core.credentials.ocra.OcraReplayVerifierBenchmark.xml` for raw timing data.

## 4. Troubleshooting
- **Benchmark skipped** – double-check `IO_OPENAUTH_SIM_BENCHMARK=true` (or pass `-Dio.openauth.sim.benchmark=true`). The Gradle output should show the property at startup.
- **Unexpected `strict_mismatch` failures** – ensure you are on the latest `main`; the harness filters mismatch vectors before timing. If failures persist, run `./gradlew :core:test --tests io.openauth.sim.core.credentials.ocra.OcraReplayVerifierTest` to confirm core verification still passes.
- **High P95 values** – rerun with `--info --rerun-tasks` to rule out transient JVM warm-up spikes. Capture system load alongside the metrics and investigate recent code changes affecting `OcraReplayVerifier`.

## 5. Related References
- `docs/4-architecture/features/009/spec.md` (ORV-NFR-002 definition).
- `docs/4-architecture/features/009/plan.md` for recording benchmark outcomes.
- `core/src/test/java/io/openauth/sim/core/credentials/ocra/OcraReplayVerifierBenchmark.java` (benchmark source).
