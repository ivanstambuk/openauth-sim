package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Placeholder regression coverage for the upcoming session-aware maintenance command.
 *
 * <p>Once T024 wires the CLI through {@code OcraResponseCalculator}, these assertions should be
 * flipped to validate the expected OTP output rather than the current error path.
 */
@TestInstance(Lifecycle.PER_CLASS)
final class MaintenanceCliSessionTest {

  private static final String STANDARD_KEY_32 =
      "3132333435363738393031323334353637383930313233343536373839303132";
  private static final String SESSION_HEX_64 =
      ("00112233445566778899AABBCCDDEEFF"
              + "102132435465768798A9BACBDCEDF0EF"
              + "112233445566778899AABBCCDDEEFF00"
              + "89ABCDEF0123456789ABCDEF01234567")
          .toUpperCase(Locale.ROOT);

  @DisplayName("Session-aware command reproduces known OTP outputs")
  @ParameterizedTest(name = "{index} ⇒ {0}")
  @MethodSource("sessionScenarios")
  void sessionAwareCommandNotYetAvailable(SessionScenario scenario) {
    MaintenanceCli cli = new MaintenanceCli();
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            scenario.arguments(),
            new PrintStream(stdout, true, StandardCharsets.UTF_8),
            new PrintStream(stderr, true, StandardCharsets.UTF_8));

    assertEquals(0, exitCode, "session helper command should succeed");
    String output = stdout.toString(StandardCharsets.UTF_8);
    assertTrue(
        output.contains("otp=" + scenario.expectedOtp()),
        () -> "expected OTP not found, stdout was: " + output);
    assertTrue(output.contains("suite=" + scenario.suite()));
    assertFalse(
        output.contains(STANDARD_KEY_32.substring(0, 6)),
        () -> "shared secret leaked in stdout: " + output);
    assertEquals("", stderr.toString(StandardCharsets.UTF_8));
  }

  private Stream<SessionScenario> sessionScenarios() {
    return Stream.of(
        new SessionScenario(
            "S064",
            "OCRA-1:HOTP-SHA256-8:QA08-S064",
            "17477202",
            new String[] {
              "ocra",
              "--suite=OCRA-1:HOTP-SHA256-8:QA08-S064",
              "--key=" + STANDARD_KEY_32,
              "--challenge=SESSION01",
              "--session=" + SESSION_HEX_64
            }),
        new SessionScenario(
            "S128",
            "OCRA-1:HOTP-SHA256-8:QA08-S128",
            "18468077",
            new String[] {
              "ocra",
              "--suite=OCRA-1:HOTP-SHA256-8:QA08-S128",
              "--key=" + STANDARD_KEY_32,
              "--challenge=SESSION01",
              "--session=" + SESSION_HEX_64 + SESSION_HEX_64
            }),
        new SessionScenario(
            "S256",
            "OCRA-1:HOTP-SHA256-8:QA08-S256",
            "77715695",
            new String[] {
              "ocra",
              "--suite=OCRA-1:HOTP-SHA256-8:QA08-S256",
              "--key=" + STANDARD_KEY_32,
              "--challenge=SESSION01",
              "--session=" + repeat(SESSION_HEX_64, 4)
            }),
        new SessionScenario(
            "S512",
            "OCRA-1:HOTP-SHA256-8:QA08-S512",
            "05806151",
            new String[] {
              "ocra",
              "--suite=OCRA-1:HOTP-SHA256-8:QA08-S512",
              "--key=" + STANDARD_KEY_32,
              "--challenge=SESSION01",
              "--session=" + repeat(SESSION_HEX_64, 8)
            }));
  }

  private static String repeat(String value, int times) {
    StringBuilder builder = new StringBuilder(value.length() * times);
    for (int i = 0; i < times; i++) {
      builder.append(value);
    }
    return builder.toString();
  }

  private record SessionScenario(
      String description, String suite, String expectedOtp, String[] arguments) {

    @Override
    public String toString() {
      return description;
    }
  }
}
