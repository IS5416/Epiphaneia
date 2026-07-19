package io.epiphaneia.infra.internal.exception;

public class InvalidConfigurationException extends EpiphaneiaException {
    public InvalidConfigurationException(String message) { super("INVALID_CONFIGURATION", message); }
}
