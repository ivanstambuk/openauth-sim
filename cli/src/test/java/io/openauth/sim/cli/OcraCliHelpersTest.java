package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
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
  void emitSkipsBlankReasonCodeAndFieldValues() throws Exception {
    OcraCli cli = new OcraCli();

    Method emit =
        OcraCli.class.getDeclaredMethod(
            "emit",
            PrintWriter.class,
            String.class,
            String.class,
            String.class,
            boolean.class,
            Map.class);
    emit.setAccessible(true);

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintWriter writer = new PrintWriter(buffer, true, StandardCharsets.UTF_8);

    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("emptyField", "   ");
    fields.put("valueField", "  present  ");

    emit.invoke(cli, writer, "cli.ocra.test", "success", "   ", true, fields);

    String output = buffer.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("event=cli.ocra.test"));
    assertTrue(output.contains("status=success"));
    assertTrue(output.contains("sanitized=true"));
    assertTrue(output.contains("valueField=present"));
    assertFalse(output.contains("reasonCode"));
    assertFalse(output.contains("emptyField"));
  }

  @Test
  void ensureParentDirectoryHandlesPathsWithoutParent() throws Exception {
    Path root = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().getRoot();
    if (root == null) {
      root = FileSystems.getDefault().getRootDirectories().iterator().next();
    }

    Method ensureParentDirectory =
        OcraCli.class.getDeclaredMethod("ensureParentDirectory", Path.class);
    ensureParentDirectory.setAccessible(true);

    ensureParentDirectory.invoke(null, root);

    assertTrue(Files.exists(root));
  }

  @Test
  void hasTextCoversNullAndBlankInput() throws Exception {
    Method hasText = OcraCli.class.getDeclaredMethod("hasText", String.class);
    hasText.setAccessible(true);

    assertFalse((boolean) hasText.invoke(null, (Object) null));
    assertFalse((boolean) hasText.invoke(null, "   "));
    assertTrue((boolean) hasText.invoke(null, "value"));
  }
}
