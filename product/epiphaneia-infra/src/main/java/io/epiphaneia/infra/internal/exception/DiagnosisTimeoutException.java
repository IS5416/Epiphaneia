package io.epiphaneia.infra.internal.exception;

/** Thrown when a diagnosis exceeds the 120s timeout. */
public class DiagnosisTimeoutException extends EpiphaneiaException {
    public DiagnosisTimeoutException(String conversationId) {
        super("DIAGNOSIS_TIMEOUT", "Diagnosis exceeded 120s limit for conversation: " + conversationId);
    }
}
