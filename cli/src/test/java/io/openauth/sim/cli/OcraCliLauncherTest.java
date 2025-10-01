package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.Permission;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class OcraCliLauncherTest {

  @Test
  void mainPrintsUsageWithoutExitingWhenExitCodeZero() {
    int exitCode = OcraCliLauncher.execute("--help");
    assertEquals(CommandLine.ExitCode.OK, exitCode);
    String usage = OcraCliLauncher.commandLine().getUsageMessage(CommandLine.Help.Ansi.OFF);
    assertTrue(usage.contains("Usage:"));
  }

  @Test
  void mainInvokesSystemExitForNonZeroCode() {
    int exitCode = OcraCliLauncher.execute("import");
    assertEquals(CommandLine.ExitCode.USAGE, exitCode);
  }

  @Test
  void mainDoesNotExitProcessWhenExitCodeZero() {
    SecurityManager original = System.getSecurityManager();
    FailOnExitSecurityManager manager = new FailOnExitSecurityManager(original);
    System.setSecurityManager(manager);
    try {
      OcraCliLauncher.main(new String[] {"--help"});
      assertFalse(manager.exitCalled);
    } finally {
      System.setSecurityManager(original);
    }
  }

  @Test
  void mainExitsProcessWhenExitCodeNonZero() {
    SecurityManager original = System.getSecurityManager();
    CapturingSecurityManager manager = new CapturingSecurityManager(original);
    System.setSecurityManager(manager);
    try {
      CapturingSecurityManager.ExitCalled thrown =
          assertThrows(
              CapturingSecurityManager.ExitCalled.class,
              () -> OcraCliLauncher.main(new String[] {"import"}));
      assertEquals(CommandLine.ExitCode.USAGE, thrown.status());
    } finally {
      System.setSecurityManager(original);
    }
  }

  private static final class FailOnExitSecurityManager extends SecurityManager {
    private final SecurityManager delegate;
    private boolean exitCalled;

    private FailOnExitSecurityManager(SecurityManager delegate) {
      this.delegate = delegate;
    }

    @Override
    public void checkPermission(Permission perm) {
      if (delegate != null) {
        delegate.checkPermission(perm);
      }
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
      if (delegate != null) {
        delegate.checkPermission(perm, context);
      }
    }

    @Override
    public void checkExit(int status) {
      exitCalled = true;
      throw new SecurityException("System.exit called unexpectedly");
    }
  }

  private static final class CapturingSecurityManager extends SecurityManager {
    private final SecurityManager delegate;

    private CapturingSecurityManager(SecurityManager delegate) {
      this.delegate = delegate;
    }

    @Override
    public void checkPermission(Permission perm) {
      if (delegate != null) {
        delegate.checkPermission(perm);
      }
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
      if (delegate != null) {
        delegate.checkPermission(perm, context);
      }
    }

    @Override
    public void checkExit(int status) {
      throw new ExitCalled(status);
    }

    private static final class ExitCalled extends SecurityException {
      private final int status;

      private ExitCalled(int status) {
        super("System.exit(" + status + ") called");
        this.status = status;
      }

      int status() {
        return status;
      }
    }
  }
}
