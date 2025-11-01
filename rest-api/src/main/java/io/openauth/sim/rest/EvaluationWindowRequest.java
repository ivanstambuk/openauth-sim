package io.openauth.sim.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvaluationWindowRequest(
        @JsonProperty("backward") Integer backward,
        @JsonProperty("forward") Integer forward) {

    public int backwardOrDefault(int fallback) {
        return sanitize(backward, fallback);
    }

    public int forwardOrDefault(int fallback) {
        return sanitize(forward, fallback);
    }

    private static int sanitize(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(0, value);
    }
}
