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
    private final Tier tier;
    private final List<TraceStep> steps;

    private VerboseTrace(String operation, Map<String, String> metadata, Tier tier, List<TraceStep> steps) {
        this.operation = operation;
        this.metadata = metadata;
        this.tier = tier;
        this.steps = steps;
    }

    public String operation() {
        return operation;
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    public Tier tier() {
        return tier;
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
        private Tier tier = Tier.EDUCATIONAL;

        private Builder(String operation) {
            this.operation = sanitize(operation);
        }

        public Builder withTier(Tier tier) {
            this.tier = Objects.requireNonNull(tier, "tier");
            return this;
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
            metadata.putIfAbsent("tier", tier.name().toLowerCase());
            return new VerboseTrace(
                    operation,
                    Collections.unmodifiableMap(new LinkedHashMap<>(metadata)),
                    tier,
                    Collections.unmodifiableList(new ArrayList<>(steps)));
        }
    }

    public static final class TraceStep {

        private final String id;
        private final String summary;
        private final String detail;
        private final String specAnchor;
        private final Map<String, Object> attributes;
        private final List<TraceAttribute> typedAttributes;
        private final Map<String, String> notes;

        private TraceStep(
                String id,
                String summary,
                String detail,
                String specAnchor,
                Map<String, Object> attributes,
                List<TraceAttribute> typedAttributes,
                Map<String, String> notes) {
            this.id = id;
            this.summary = summary;
            this.detail = detail;
            this.specAnchor = specAnchor;
            this.attributes = attributes;
            this.typedAttributes = typedAttributes;
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

        public String specAnchor() {
            return specAnchor;
        }

        public Map<String, Object> attributes() {
            return attributes;
        }

        public List<TraceAttribute> typedAttributes() {
            return typedAttributes;
        }

        public Map<String, String> notes() {
            return notes;
        }

        public static final class Builder {

            private String id;
            private String summary;
            private String detail;
            private String specAnchor;
            private final Map<String, Object> attributes = new LinkedHashMap<>();
            private final List<TraceAttribute> typedAttributes = new ArrayList<>();
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

            public Builder spec(String anchor) {
                this.specAnchor = sanitize(anchor);
                return this;
            }

            public Builder attribute(String key, Object value) {
                return attribute(AttributeType.STRING, key, value);
            }

            public Builder attribute(AttributeType type, String key, Object value) {
                AttributeType attributeType = Objects.requireNonNull(type, "type");
                String sanitizedKey = sanitize(key);
                Object sanitizedValue = Objects.requireNonNullElse(value, "");
                attributes.put(sanitizedKey, sanitizedValue);
                typedAttributes.add(new TraceAttribute(attributeType, sanitizedKey, sanitizedValue));
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
                        specAnchor,
                        Collections.unmodifiableMap(new LinkedHashMap<>(attributes)),
                        Collections.unmodifiableList(new ArrayList<>(typedAttributes)),
                        Collections.unmodifiableMap(new LinkedHashMap<>(notes)));
            }
        }
    }

    public enum Tier {
        NORMAL,
        EDUCATIONAL,
        LAB_SECRETS
    }

    public enum AttributeType {
        STRING("string"),
        INT("int"),
        HEX("hex"),
        BASE64URL("base64url"),
        BOOL("bool"),
        JSON("json"),
        BYTES("bytes");

        private final String label;

        AttributeType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record TraceAttribute(AttributeType type, String name, Object value) {
        public TraceAttribute {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }
    }

    private static String sanitize(String value) {
        Objects.requireNonNull(value, "value");
        return value.trim();
    }
}
