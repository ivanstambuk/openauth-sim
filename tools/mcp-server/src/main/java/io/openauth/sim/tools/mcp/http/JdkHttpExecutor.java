package io.openauth.sim.tools.mcp.http;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class JdkHttpExecutor implements HttpExecutor {
    private final HttpClient client;

    public JdkHttpExecutor() {
        this(HttpClient.newBuilder().build());
    }

    public JdkHttpExecutor(HttpClient client) {
        this.client = client;
    }

    @Override
    public HttpResponse<String> execute(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
