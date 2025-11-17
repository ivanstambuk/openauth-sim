package io.openauth.sim.tools.mcp.tool;

import java.net.http.HttpRequest;

public record RestToolInvocation(String toolName, HttpRequest request) {}
