package io.epiphaneia.infra.api.exception;

/** Base exception for all Epiphaneia errors. Public API — thrown across module boundaries. */
public class EpiphaneiaException extends RuntimeException {

    private final String errorCode;

    public EpiphaneiaException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public EpiphaneiaException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
