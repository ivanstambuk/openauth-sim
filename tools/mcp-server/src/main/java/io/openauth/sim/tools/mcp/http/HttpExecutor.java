package io.openauth.sim.tools.mcp.http;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public interface HttpExecutor {
    HttpResponse<String> execute(HttpRequest request) throws IOException, InterruptedException;
}
