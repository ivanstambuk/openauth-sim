package io.openauth.sim.cli.support;

import java.nio.file.Path;

public final class JsonShapeAsserter {

    private JsonShapeAsserter() {}

    public static void assertMatchesShape(Path schemaPath, String actualJson) {
        io.openauth.sim.testing.JsonShapeAsserter.assertMatchesShape(schemaPath, actualJson);
    }

    public static void assertMatchesShape(Object schema, String actualJson) {
        io.openauth.sim.testing.JsonShapeAsserter.assertMatchesShape(schema, actualJson);
    }
}
