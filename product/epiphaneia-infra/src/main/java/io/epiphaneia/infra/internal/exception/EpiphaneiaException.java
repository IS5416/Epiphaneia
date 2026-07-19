package io.epiphaneia.infra.internal.exception;

/** Base exception for all Epiphaneia errors. */
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
