package io.openauth.sim.application.ocra;

import io.openauth.sim.application.ocra.OcraEvaluationApplicationService.EvaluationCommand;
import java.time.Duration;
import java.util.Objects;

public final class OcraEvaluationRequests {

    private OcraEvaluationRequests() {
        throw new AssertionError("No instances");
    }

    public static EvaluationCommand stored(StoredInputs inputs) {
        Objects.requireNonNull(inputs, "inputs");
        return new EvaluationCommand.Stored(
                trimRequired(inputs.credentialId(), "credentialId"),
                trim(inputs.challenge()),
                trim(inputs.sessionHex()),
                trim(inputs.clientChallenge()),
                trim(inputs.serverChallenge()),
                trim(inputs.pinHashHex()),
                trim(inputs.timestampHex()),
                inputs.counter());
    }

    public static EvaluationCommand inline(InlineInputs inputs) {
        Objects.requireNonNull(inputs, "inputs");
        return new EvaluationCommand.Inline(
                trim(inputs.identifier()),
                trimRequired(inputs.suite(), "suite"),
                normalizeHex(inputs.sharedSecretHex(), "sharedSecretHex"),
                trim(inputs.challenge()),
                trim(inputs.sessionHex()),
                trim(inputs.clientChallenge()),
                trim(inputs.serverChallenge()),
                normalizeHex(inputs.pinHashHex(), "pinHashHex"),
                normalizeHex(inputs.timestampHex(), "timestampHex"),
                inputs.counter(),
                inputs.allowedDrift());
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String trimRequired(String value, String field) {
        String trimmed = trim(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return trimmed;
    }

    private static String normalizeHex(String value, String field) {
        String trimmed = trim(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.replace(" ", "");
    }

    public record StoredInputs(
            String credentialId,
            String challenge,
            String sessionHex,
            String clientChallenge,
            String serverChallenge,
            String pinHashHex,
            String timestampHex,
            Long counter) {
        // Data carrier for stored OCRA evaluation inputs.
    }

    public record InlineInputs(
            String identifier,
            String suite,
            String sharedSecretHex,
            String challenge,
            String sessionHex,
            String clientChallenge,
            String serverChallenge,
            String pinHashHex,
            String timestampHex,
            Long counter,
            Duration allowedDrift) {
        // Data carrier for inline OCRA evaluation inputs.
    }
}
