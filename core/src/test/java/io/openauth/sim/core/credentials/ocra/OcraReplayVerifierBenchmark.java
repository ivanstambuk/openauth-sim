package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraInlineVerificationRequest;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraStoredVerificationRequest;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationContext;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationResult;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationStatus;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.MapDbCredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * Benchmark harness for {@link OcraReplayVerifier}. Enable with {@code
 * -Dio.openauth.sim.benchmark=true} to capture stored vs inline verification latency.
 */
final class OcraReplayVerifierBenchmark {

  private static final Logger LOGGER =
      Logger.getLogger(OcraReplayVerifierBenchmark.class.getName());

  private static final String BENCHMARK_FLAG = "io.openauth.sim.benchmark";
  private static final int WARMUP_OPERATIONS = 2_000;
  private static final int MEASURED_OPERATIONS = 20_000;

  private final OcraCredentialFactory credentialFactory = new OcraCredentialFactory();
  private final OcraCredentialPersistenceAdapter persistenceAdapter =
      new OcraCredentialPersistenceAdapter();

  @Test
  void storedCredentialVerificationLatency() {
    assumeTrue(isBenchmarkEnabled(), "Benchmark flag not enabled");

    LOGGER.info(() -> "Starting stored verification benchmark; env=" + environmentSummary());

    List<OcraRfc6287VectorFixtures.OneWayVector> vectors = storedVectors();

    try (MapDbCredentialStore store = MapDbCredentialStore.inMemory().open()) {
      List<OcraStoredVerificationRequest> requests = prepareStoredRequests(store, vectors);
      OcraReplayVerifier verifier = new OcraReplayVerifier(store);
      List<OcraStoredVerificationRequest> matchRequests = filterStoredMatches(verifier, requests);

      warmUpStored(verifier, matchRequests, WARMUP_OPERATIONS);
      BenchmarkReport report = measureStored(verifier, matchRequests, MEASURED_OPERATIONS);
      logReport("stored", report);
    }
  }

  @Test
  void inlineCredentialVerificationLatency() {
    assumeTrue(isBenchmarkEnabled(), "Benchmark flag not enabled");

    LOGGER.info(() -> "Starting inline verification benchmark; env=" + environmentSummary());

    List<OcraRfc6287VectorFixtures.OneWayVector> vectors = inlineVectors();
    List<OcraInlineVerificationRequest> requests = prepareInlineRequests(vectors);
    OcraReplayVerifier verifier = new OcraReplayVerifier(null);
    List<OcraInlineVerificationRequest> matchRequests = filterInlineMatches(verifier, requests);

    warmUpInline(verifier, matchRequests, WARMUP_OPERATIONS);
    BenchmarkReport report = measureInline(verifier, matchRequests, MEASURED_OPERATIONS);
    logReport("inline", report);
  }

  private List<OcraStoredVerificationRequest> prepareStoredRequests(
      MapDbCredentialStore store, List<OcraRfc6287VectorFixtures.OneWayVector> vectors) {
    List<OcraStoredVerificationRequest> requests = new ArrayList<>(vectors.size());
    int index = 0;
    for (OcraRfc6287VectorFixtures.OneWayVector vector : vectors) {
      String credentialId = "benchmark-stored-" + index++;
      OcraCredentialFactory.OcraCredentialRequest descriptorRequest =
          new OcraCredentialFactory.OcraCredentialRequest(
              credentialId,
              vector.ocraSuite(),
              vector.sharedSecretHex(),
              vector.secretEncoding(),
              vector.counter(),
              vector.pinHashHex(),
              null,
              Map.of("source", "benchmark"));

      OcraCredentialDescriptor descriptor = credentialFactory.createDescriptor(descriptorRequest);
      store.save(toCredential(descriptor));

      OcraVerificationContext context =
          new OcraVerificationContext(
              vector.counter(),
              vector.question(),
              vector.sessionInformation(),
              null,
              null,
              vector.pinHashHex(),
              vector.timestampHex());

      requests.add(new OcraStoredVerificationRequest(credentialId, vector.expectedOtp(), context));
    }
    return requests;
  }

  private List<OcraInlineVerificationRequest> prepareInlineRequests(
      List<OcraRfc6287VectorFixtures.OneWayVector> vectors) {
    List<OcraInlineVerificationRequest> requests = new ArrayList<>(vectors.size());
    int index = 0;
    for (OcraRfc6287VectorFixtures.OneWayVector vector : vectors) {
      OcraVerificationContext context =
          new OcraVerificationContext(
              vector.counter(),
              vector.question(),
              vector.sessionInformation(),
              null,
              null,
              vector.pinHashHex(),
              vector.timestampHex());

      requests.add(
          new OcraInlineVerificationRequest(
              "benchmark-inline-" + index++,
              vector.ocraSuite(),
              vector.sharedSecretHex(),
              vector.secretEncoding(),
              vector.expectedOtp(),
              context,
              Map.of("source", "benchmark")));
    }
    return requests;
  }

  private List<OcraStoredVerificationRequest> filterStoredMatches(
      OcraReplayVerifier verifier, List<OcraStoredVerificationRequest> requests) {
    List<OcraStoredVerificationRequest> matches = new ArrayList<>(requests.size());
    for (OcraStoredVerificationRequest request : requests) {
      OcraVerificationResult result = verifier.verifyStored(request);
      if (result.status() == OcraVerificationStatus.MATCH) {
        matches.add(request);
      } else {
        LOGGER.log(
            Level.FINE,
            () ->
                "Skipping stored verification scenario `"
                    + request.credentialId()
                    + "` due to result="
                    + result);
      }
    }
    if (matches.isEmpty()) {
      throw new IllegalStateException("No stored verification scenarios produced a match");
    }
    return matches;
  }

  private List<OcraInlineVerificationRequest> filterInlineMatches(
      OcraReplayVerifier verifier, List<OcraInlineVerificationRequest> requests) {
    List<OcraInlineVerificationRequest> matches = new ArrayList<>(requests.size());
    for (OcraInlineVerificationRequest request : requests) {
      OcraVerificationResult result = verifier.verifyInline(request);
      if (result.status() == OcraVerificationStatus.MATCH) {
        matches.add(request);
      } else {
        LOGGER.log(
            Level.FINE,
            () ->
                "Skipping inline verification scenario `"
                    + request.descriptorName()
                    + "` due to result="
                    + result);
      }
    }
    if (matches.isEmpty()) {
      throw new IllegalStateException("No inline verification scenarios produced a match");
    }
    return matches;
  }

  private static void warmUpStored(
      OcraReplayVerifier verifier, List<OcraStoredVerificationRequest> requests, int iterations) {
    if (iterations <= 0) {
      return;
    }
    int size = requests.size();
    for (int i = 0; i < iterations; i++) {
      OcraStoredVerificationRequest request = requests.get(i % size);
      verifier.verifyStored(request);
    }
  }

  private static void warmUpInline(
      OcraReplayVerifier verifier, List<OcraInlineVerificationRequest> requests, int iterations) {
    if (iterations <= 0) {
      return;
    }
    int size = requests.size();
    for (int i = 0; i < iterations; i++) {
      OcraInlineVerificationRequest request = requests.get(i % size);
      verifier.verifyInline(request);
    }
  }

  private static BenchmarkReport measureStored(
      OcraReplayVerifier verifier, List<OcraStoredVerificationRequest> requests, int iterations) {
    List<Long> latencies = new ArrayList<>(iterations);
    long totalStart = System.nanoTime();
    int size = requests.size();
    for (int i = 0; i < iterations; i++) {
      OcraStoredVerificationRequest request = requests.get(i % size);
      long start = System.nanoTime();
      OcraVerificationResult result = verifier.verifyStored(request);
      long end = System.nanoTime();
      ensureMatch(result);
      latencies.add(end - start);
    }
    long totalEnd = System.nanoTime();
    return new BenchmarkReport(iterations, Duration.ofNanos(totalEnd - totalStart), latencies);
  }

  private static BenchmarkReport measureInline(
      OcraReplayVerifier verifier, List<OcraInlineVerificationRequest> requests, int iterations) {
    List<Long> latencies = new ArrayList<>(iterations);
    long totalStart = System.nanoTime();
    int size = requests.size();
    for (int i = 0; i < iterations; i++) {
      OcraInlineVerificationRequest request = requests.get(i % size);
      long start = System.nanoTime();
      OcraVerificationResult result = verifier.verifyInline(request);
      long end = System.nanoTime();
      ensureMatch(result);
      latencies.add(end - start);
    }
    long totalEnd = System.nanoTime();
    return new BenchmarkReport(iterations, Duration.ofNanos(totalEnd - totalStart), latencies);
  }

  private static void ensureMatch(OcraVerificationResult result) {
    if (result.status() != OcraVerificationStatus.MATCH) {
      throw new IllegalStateException("benchmark run produced non-match result: " + result);
    }
  }

  private void logReport(String mode, BenchmarkReport report) {
    LOGGER.log(
        Level.INFO,
        () ->
            String.format(
                Locale.ROOT,
                "ocra-replay.%s warmup=%d measured=%d totalMs=%.3f throughputOpsPerSec=%.2f p50Ms=%.3f p90Ms=%.3f p95Ms=%.3f p99Ms=%.3f maxMs=%.3f",
                mode,
                WARMUP_OPERATIONS,
                MEASURED_OPERATIONS,
                report.totalDuration().toNanos() / 1_000_000.0,
                report.getThroughputOpsPerSecond(),
                report.getLatencyMillis(0.50),
                report.getLatencyMillis(0.90),
                report.getLatencyMillis(0.95),
                report.getLatencyMillis(0.99),
                report.getMaxLatencyMillis()));
  }

  private boolean isBenchmarkEnabled() {
    if (Boolean.getBoolean(BENCHMARK_FLAG)) {
      return true;
    }
    String env = System.getenv("IO_OPENAUTH_SIM_BENCHMARK");
    return env != null && Boolean.parseBoolean(env);
  }

  private static String environmentSummary() {
    String osName = System.getProperty("os.name", "unknown");
    String osVersion = System.getProperty("os.version", "unknown");
    String osArch = System.getProperty("os.arch", "unknown");
    String javaRuntime = System.getProperty("java.runtime.name", "unknown");
    String javaVersion = System.getProperty("java.runtime.version", "unknown");
    int processors = Runtime.getRuntime().availableProcessors();
    Optional<String> wsl = Optional.ofNullable(System.getenv("WSL_DISTRO_NAME"));
    return String.format(
        Locale.ROOT,
        "%s %s (%s), java=%s %s, cores=%d%s",
        osName,
        osVersion,
        osArch,
        javaRuntime,
        javaVersion,
        processors,
        wsl.map(name -> ", wslDistro=" + name).orElse(""));
  }

  private Credential toCredential(OcraCredentialDescriptor descriptor) {
    return VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor));
  }

  private static List<OcraRfc6287VectorFixtures.OneWayVector> storedVectors() {
    List<OcraRfc6287VectorFixtures.OneWayVector> vectors = new ArrayList<>();
    vectors.addAll(OcraRfc6287VectorFixtures.counterAndPinVectors());
    vectors.addAll(OcraRfc6287VectorFixtures.timeBasedSha512Vectors());
    vectors.addAll(OcraRfc6287VectorFixtures.sessionInformationVectors());
    return Collections.unmodifiableList(vectors);
  }

  private static List<OcraRfc6287VectorFixtures.OneWayVector> inlineVectors() {
    List<OcraRfc6287VectorFixtures.OneWayVector> vectors = new ArrayList<>();
    vectors.addAll(OcraRfc6287VectorFixtures.standardChallengeQuestionVectors());
    vectors.addAll(OcraRfc6287VectorFixtures.hashedPinWithoutCounterVectors());
    vectors.addAll(OcraRfc6287VectorFixtures.timeBasedSha512Vectors());
    vectors.addAll(OcraRfc6287VectorFixtures.sessionInformationVectors());
    return Collections.unmodifiableList(vectors);
  }

  private record BenchmarkReport(int iterations, Duration totalDuration, List<Long> latencies) {

    BenchmarkReport {
      latencies = List.copyOf(latencies);
    }

    double getThroughputOpsPerSecond() {
      double seconds = Math.max(1e-9, totalDuration.toNanos() / 1_000_000_000.0);
      return iterations / seconds;
    }

    double getLatencyMillis(double percentile) {
      if (latencies.isEmpty()) {
        return 0.0;
      }
      List<Long> sorted = new ArrayList<>(latencies);
      Collections.sort(sorted);
      int index = Math.min(sorted.size() - 1, (int) Math.ceil(percentile * sorted.size()) - 1);
      long nanos = sorted.get(Math.max(index, 0));
      return nanos / 1_000_000.0;
    }

    double getMaxLatencyMillis() {
      if (latencies.isEmpty()) {
        return 0.0;
      }
      long max = 0L;
      for (long value : latencies) {
        if (value > max) {
          max = value;
        }
      }
      return max / 1_000_000.0;
    }
  }
}
