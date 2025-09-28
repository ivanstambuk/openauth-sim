package io.openauth.sim.core.store;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * Baseline benchmark harness for {@link MapDbCredentialStore}. Run manually with {@code
 * -Dio.openauth.sim.benchmark=true} to capture current performance metrics.
 */
final class MapDbCredentialStoreBaselineBenchmark {

  private static final Logger LOGGER =
      Logger.getLogger(MapDbCredentialStoreBaselineBenchmark.class.getName());

  private static final String BENCHMARK_FLAG = "io.openauth.sim.benchmark";
  private static final int DATASET_SIZE = 20_000;
  private static final int READ_OPERATIONS = 50_000;

  @Test
  void runBaselineBenchmark() {
    assumeTrue(isBenchmarkEnabled(), "Benchmark flag not enabled");

    LOGGER.info(
        () -> "Starting MapDbCredentialStore baseline benchmark (dataset=" + DATASET_SIZE + ")");

    List<Credential> credentials = generateCredentials(DATASET_SIZE);

    try (MapDbCredentialStore store = MapDbCredentialStore.inMemory().open()) {
      BenchmarkReport report = executeBenchmark(store, credentials, READ_OPERATIONS);
      logReport("IN_MEMORY", report);
    }
  }

  private static boolean isBenchmarkEnabled() {
    if (Boolean.getBoolean(BENCHMARK_FLAG)) {
      return true;
    }
    String env = System.getenv("IO_OPENAUTH_SIM_BENCHMARK");
    return env != null && Boolean.parseBoolean(env);
  }

  private static BenchmarkReport executeBenchmark(
      MapDbCredentialStore store, List<Credential> credentials, int readOperations) {

    long writesStart = System.nanoTime();
    for (Credential credential : credentials) {
      store.save(credential);
    }
    long writesEnd = System.nanoTime();

    List<Long> latenciesNanos = new ArrayList<>(readOperations);
    Random random = ThreadLocalRandom.current();
    long readsStart = System.nanoTime();
    for (int i = 0; i < readOperations; i++) {
      String name = credentials.get(random.nextInt(credentials.size())).name();
      long start = System.nanoTime();
      store.findByName(name).orElseThrow();
      long end = System.nanoTime();
      latenciesNanos.add(end - start);
    }
    long readsEnd = System.nanoTime();

    return new BenchmarkReport(
        credentials.size(),
        Duration.ofNanos(writesEnd - writesStart),
        readOperations,
        Duration.ofNanos(readsEnd - readsStart),
        latenciesNanos);
  }

  private static List<Credential> generateCredentials(int count) {
    List<Credential> credentials = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      String name = "benchmark-" + i;
      String secret = String.format(Locale.ROOT, "%032x", i);
      credentials.add(
          Credential.create(
              name,
              CredentialType.OATH_OCRA,
              SecretMaterial.fromHex(secret),
              Collections.emptyMap()));
    }
    return credentials;
  }

  private static void logReport(String profile, BenchmarkReport report) {
    LOGGER.log(
        Level.INFO,
        () ->
            "Benchmark profile="
                + profile
                + " writes="
                + report.datasetSize
                + " writeDurationMs="
                + report.writeDuration.toMillis()
                + " writesPerSecond="
                + report.getWritesPerSecond()
                + " reads="
                + report.readOperations
                + " readDurationMs="
                + report.readDuration.toMillis()
                + " readsPerSecond="
                + report.getReadsPerSecond()
                + " p50Ms="
                + report.getLatencyMillis(0.50)
                + " p90Ms="
                + report.getLatencyMillis(0.90)
                + " p99Ms="
                + report.getLatencyMillis(0.99));
  }

  private static final class BenchmarkReport {
    private final int datasetSize;
    private final Duration writeDuration;
    private final int readOperations;
    private final Duration readDuration;
    private final List<Long> latenciesNanos;
    private List<Long> sortedCache;

    private BenchmarkReport(
        int datasetSize,
        Duration writeDuration,
        int readOperations,
        Duration readDuration,
        List<Long> latenciesNanos) {
      this.datasetSize = datasetSize;
      this.writeDuration = writeDuration;
      this.readOperations = readOperations;
      this.readDuration = readDuration;
      this.latenciesNanos = List.copyOf(latenciesNanos);
    }

    double getWritesPerSecond() {
      double seconds = Math.max(1e-9, writeDuration.toNanos() / 1_000_000_000.0);
      return datasetSize / seconds;
    }

    double getReadsPerSecond() {
      double seconds = Math.max(1e-9, readDuration.toNanos() / 1_000_000_000.0);
      return readOperations / seconds;
    }

    double getLatencyMillis(double percentile) {
      if (latenciesNanos.isEmpty()) {
        return 0.0;
      }
      List<Long> sorted = sortedLatencies();
      int index = Math.min(sorted.size() - 1, (int) Math.ceil(percentile * sorted.size()) - 1);
      long nanos = sorted.get(Math.max(index, 0));
      return nanos / 1_000_000.0;
    }

    private List<Long> sortedLatencies() {
      if (sortedCache == null) {
        sortedCache = new ArrayList<>(latenciesNanos);
        Collections.sort(sortedCache);
      }
      return sortedCache;
    }
  }
}
