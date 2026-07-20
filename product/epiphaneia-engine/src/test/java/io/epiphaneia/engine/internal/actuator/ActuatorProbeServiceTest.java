package io.epiphaneia.engine.internal.actuator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActuatorProbeServiceTest {

    @Test
    @DisplayName("sensitive keys are detected")
    void sensitiveKeys() {
        assertTrue(ActuatorProbeService.isSensitiveKey("DB_PASSWORD"));
        assertTrue(ActuatorProbeService.isSensitiveKey("api_credential"));
        assertTrue(ActuatorProbeService.isSensitiveKey("JWT_TOKEN"));
        assertTrue(ActuatorProbeService.isSensitiveKey("encryption_private_key"));
    }

    @Test
    @DisplayName("non-sensitive keys pass through")
    void nonSensitiveKeys() {
        assertFalse(ActuatorProbeService.isSensitiveKey("SERVER_PORT"));
        assertFalse(ActuatorProbeService.isSensitiveKey("JAVA_HOME"));
        assertFalse(ActuatorProbeService.isSensitiveKey("spring.application.name"));
    }

    @Test
    @DisplayName("null key is safe")
    void nullKey() {
        assertFalse(ActuatorProbeService.isSensitiveKey(null));
    }

    @Test
    @DisplayName("empty key is safe")
    void emptyKey() {
        assertFalse(ActuatorProbeService.isSensitiveKey(""));
    }
}
