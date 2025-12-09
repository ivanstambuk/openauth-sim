package io.openauth.sim.application.eudi.openid4vp.fixtures;

import io.openauth.sim.application.eudi.openid4vp.OpenId4VpAuthorizationRequestService;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
        Properties properties = new Properties();
        boolean loaded = loadFromClasspath(properties);
        if (!loaded) {
            loaded = loadFromFilesystem(properties);
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

    private static boolean loadFromClasspath(Properties properties) {
        try (InputStream stream =
                FixtureSeedSequence.class.getClassLoader().getResourceAsStream("eudiw/openid4vp/seeds/default.seed")) {
            if (stream == null) {
                return false;
            }
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private static boolean loadFromFilesystem(Properties properties) {
        Path seedsFile = FixturePaths.resolve("docs", "test-vectors", "eudiw", "openid4vp", "seeds", "default.seed");
        try {
            properties.load(Files.newBufferedReader(seedsFile, StandardCharsets.UTF_8));
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}
