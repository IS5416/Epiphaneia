package io.epiphaneia.infra.internal.exception;

import io.epiphaneia.infra.api.exception.EpiphaneiaException;

public class InvalidConfigurationException extends EpiphaneiaException {
    public InvalidConfigurationException(String message) { super("INVALID_CONFIGURATION", message); }
}
