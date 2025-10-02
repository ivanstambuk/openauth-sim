package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * Targeted tests for OcraCli helper branches that are otherwise hard to reach via end-to-end flows.
 */
final class OcraCliHelpersTest {

  @Test
  void callDisplaysUsageAndReturnsUsageExitCode() {
    OcraCli cli = new OcraCli();
    CommandLine commandLine = new CommandLine(cli);

    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    commandLine.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));

    int exitCode = commandLine.execute();

    assertEquals(CommandLine.ExitCode.USAGE, exitCode);
    String renderedUsage = stdout.toString(StandardCharsets.UTF_8);
    assertTrue(renderedUsage.contains("Manage OCRA credentials"));
    assertTrue(renderedUsage.contains("ocra"));
  }

  @Test
  void emitSkipsBlankReasonCodeAndFieldValues() {
    OcraCli cli = new OcraCli();

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintWriter writer = new PrintWriter(buffer, true, StandardCharsets.UTF_8);

    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("emptyField", "   ");
    fields.put("valueField", "  present  ");

    cli.emit(writer, "cli.ocra.test", "success", "   ", true, fields);

    String output = buffer.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("event=cli.ocra.test"));
    assertTrue(output.contains("status=success"));
    assertTrue(output.contains("sanitized=true"));
    assertTrue(output.contains("valueField=present"));
    assertFalse(output.contains("reasonCode"));
    assertFalse(output.contains("emptyField"));
  }

  @Test
  void hasTextCoversNullAndBlankInput() {
    assertFalse(OcraCli.hasText(null));
    assertFalse(OcraCli.hasText("   "));
    assertTrue(OcraCli.hasText("value"));
  }
}
