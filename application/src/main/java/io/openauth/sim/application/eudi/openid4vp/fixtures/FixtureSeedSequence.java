package io.openauth.sim.application.eudi.openid4vp.fixtures;

import io.openauth.sim.application.eudi.openid4vp.OpenId4VpAuthorizationRequestService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public final class FixtureSeedSequence implements OpenId4VpAuthorizationRequestService.SeedSequence {
    private final AtomicInteger requestCounter = new AtomicInteger();
    private final AtomicInteger nonceCounter = new AtomicInteger();
    private final AtomicInteger stateCounter = new AtomicInteger();
    private final String nonceSeed;
    private final String stateSeed;
    private final String requestIdPrefix;

    public FixtureSeedSequence() {
        Path seedsFile = FixturePaths.resolve("docs", "test-vectors", "eudiw", "openid4vp", "seeds", "default.seed");
        Properties properties = new Properties();
        try {
            properties.load(Files.newBufferedReader(seedsFile, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read OpenID4VP seed file " + seedsFile, ex);
        }
        this.nonceSeed = properties.getProperty("nonceSeed", "oid4vp-nonce");
        this.stateSeed = properties.getProperty("stateSeed", "oid4vp-state");
        this.requestIdPrefix = properties.getProperty("requestIdPrefix", "OID4VP-");
    }

    @Override
    public String nextRequestId() {
        return requestIdPrefix + pad(requestCounter.incrementAndGet());
    }

    @Override
    public String nextNonce() {
        return nonceSeed + "-" + pad(nonceCounter.incrementAndGet());
    }

    @Override
    public String nextState() {
        return stateSeed + "-" + pad(stateCounter.incrementAndGet());
    }

    private static String pad(int value) {
        return String.format("%04d", value);
    }
}
