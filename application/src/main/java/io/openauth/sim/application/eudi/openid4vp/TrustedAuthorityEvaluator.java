package io.openauth.sim.application.eudi.openid4vp;

import io.openauth.sim.core.eudi.openid4vp.TrustedAuthorityFixtures.TrustedAuthorityPolicy;
import io.openauth.sim.core.eudi.openid4vp.TrustedAuthorityFixtures.TrustedAuthoritySnapshot;
import io.openauth.sim.core.eudi.openid4vp.TrustedAuthorityFixtures.TrustedAuthorityValue;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class TrustedAuthorityEvaluator {

    private final Map<String, Map<String, String>> labelsByType;

    private TrustedAuthorityEvaluator(Map<String, Map<String, String>> labelsByType) {
        this.labelsByType = labelsByType;
    }

    static TrustedAuthorityEvaluator fromSnapshot(TrustedAuthoritySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Map<String, Map<String, String>> index = new HashMap<>();
        for (TrustedAuthorityPolicy policy : snapshot.authorities()) {
            Map<String, String> values = index.computeIfAbsent(policy.type(), key -> new HashMap<>());
            for (TrustedAuthorityValue value : policy.values()) {
                values.put(value.value(), value.label());
            }
        }
        Map<String, Map<String, String>> immutable = new HashMap<>();
        index.forEach((key, value) -> immutable.put(key, Map.copyOf(value)));
        return new TrustedAuthorityEvaluator(Collections.unmodifiableMap(immutable));
    }

    Decision evaluate(Optional<String> requestedPolicy, List<String> availablePolicies) {
        Optional<String> sanitizedPolicy = sanitize(requestedPolicy);
        if (sanitizedPolicy.isEmpty()) {
            return Decision.noop();
        }
        List<String> policies = availablePolicies == null ? List.of() : List.copyOf(availablePolicies);
        String policy = sanitizedPolicy.get();
        if (!policies.contains(policy)) {
            String detail = "Trusted Authority policy " + policy + " not satisfied by wallet";
            return Decision.rejected(Oid4vpProblemDetailsMapper.invalidScope(detail));
        }
        TrustedAuthorityVerdict match = buildVerdict(policy);
        return Decision.accepted(match);
    }

    List<TrustedAuthorityVerdict> describePolicies(List<String> policies) {
        if (policies == null || policies.isEmpty()) {
            return List.of();
        }
        return policies.stream().map(this::buildVerdict).toList();
    }

    private static Optional<String> sanitize(Optional<String> requestedPolicy) {
        if (requestedPolicy == null) {
            return Optional.empty();
        }
        return requestedPolicy.map(String::trim).filter(value -> !value.isEmpty());
    }

    private TrustedAuthorityVerdict buildVerdict(String policy) {
        String type = extractType(policy);
        String value = extractValue(policy);
        String label = labelsByType.getOrDefault(type, Map.of()).getOrDefault(value, value);
        return new TrustedAuthorityVerdict(type, value, label, policy);
    }

    private static String extractType(String policy) {
        int separator = policy.indexOf(':');
        if (separator <= 0) {
            return "unknown";
        }
        return policy.substring(0, separator);
    }

    private static String extractValue(String policy) {
        int separator = policy.indexOf(':');
        if (separator < 0 || separator == policy.length() - 1) {
            return policy;
        }
        return policy.substring(separator + 1);
    }

    record Decision(
            Optional<TrustedAuthorityVerdict> trustedAuthorityMatch, Optional<Oid4vpProblemDetails> problemDetails) {
        Decision {
            trustedAuthorityMatch = trustedAuthorityMatch == null ? Optional.empty() : trustedAuthorityMatch;
            problemDetails = problemDetails == null ? Optional.empty() : problemDetails;
        }

        static Decision noop() {
            return new Decision(Optional.empty(), Optional.empty());
        }

        static Decision accepted(TrustedAuthorityVerdict match) {
            return new Decision(Optional.of(match), Optional.empty());
        }

        static Decision rejected(Oid4vpProblemDetails problemDetails) {
            return new Decision(Optional.empty(), Optional.of(problemDetails));
        }
    }

    record TrustedAuthorityVerdict(String type, String value, String label, String policy) {
        TrustedAuthorityVerdict {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(policy, "policy");
        }
    }
}
