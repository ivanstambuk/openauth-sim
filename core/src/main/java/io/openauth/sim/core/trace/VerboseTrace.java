package io.openauth.sim.core.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Immutable verbose trace describing algorithm execution steps for audit purposes. */
public final class VerboseTrace {

    private final String operation;
    private final Map<String, String> metadata;
    private final List<TraceStep> steps;

    private VerboseTrace(String operation, Map<String, String> metadata, List<TraceStep> steps) {
        this.operation = operation;
        this.metadata = metadata;
        this.steps = steps;
    }

    public String operation() {
        return operation;
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    public List<TraceStep> steps() {
        return steps;
    }

    public static Builder builder(String operation) {
        return new Builder(operation);
    }

    public static final class Builder {

        private final String operation;
        private final Map<String, String> metadata = new LinkedHashMap<>();
        private final List<TraceStep> steps = new ArrayList<>();

        private Builder(String operation) {
            this.operation = sanitize(operation);
        }

        public Builder withMetadata(String key, String value) {
            metadata.put(sanitize(key), sanitize(value));
            return this;
        }

        public Builder addStep(Consumer<TraceStep.Builder> configurer) {
            Objects.requireNonNull(configurer, "configurer");
            TraceStep.Builder builder = new TraceStep.Builder();
            configurer.accept(builder);
            steps.add(builder.build());
            return this;
        }

        public VerboseTrace build() {
            return new VerboseTrace(
                    operation,
                    Collections.unmodifiableMap(new LinkedHashMap<>(metadata)),
                    Collections.unmodifiableList(new ArrayList<>(steps)));
        }
    }

    public static final class TraceStep {

        private final String id;
        private final String summary;
        private final String detail;
        private final Map<String, Object> attributes;
        private final Map<String, String> notes;

        private TraceStep(
                String id, String summary, String detail, Map<String, Object> attributes, Map<String, String> notes) {
            this.id = id;
            this.summary = summary;
            this.detail = detail;
            this.attributes = attributes;
            this.notes = notes;
        }

        public String id() {
            return id;
        }

        public String summary() {
            return summary;
        }

        public String detail() {
            return detail;
        }

        public Map<String, Object> attributes() {
            return attributes;
        }

        public Map<String, String> notes() {
            return notes;
        }

        public static final class Builder {

            private String id;
            private String summary;
            private String detail;
            private final Map<String, Object> attributes = new LinkedHashMap<>();
            private final Map<String, String> notes = new LinkedHashMap<>();

            public Builder id(String id) {
                this.id = sanitize(id);
                return this;
            }

            public Builder summary(String summary) {
                this.summary = sanitize(summary);
                return this;
            }

            public Builder detail(String detail) {
                this.detail = sanitize(detail);
                return this;
            }

            public Builder attribute(String key, Object value) {
                attributes.put(sanitize(key), Objects.requireNonNullElse(value, ""));
                return this;
            }

            public Builder note(String key, String value) {
                notes.put(sanitize(key), sanitize(value));
                return this;
            }

            private TraceStep build() {
                String builtId = Objects.requireNonNull(id, "id");
                return new TraceStep(
                        builtId,
                        summary,
                        detail,
                        Collections.unmodifiableMap(new LinkedHashMap<>(attributes)),
                        Collections.unmodifiableMap(new LinkedHashMap<>(notes)));
            }
        }
    }

    private static String sanitize(String value) {
        Objects.requireNonNull(value, "value");
        return value.trim();
    }
}
