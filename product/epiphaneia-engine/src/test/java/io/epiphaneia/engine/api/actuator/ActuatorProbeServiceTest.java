package io.epiphaneia.engine.api.actuator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

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

    @Test
    @DisplayName("IPv4-mapped IPv6 loopback is forbidden (SSRF bypass attempt)")
    void ipv4MappedIpv6LoopbackForbidden() throws Exception {
        // ::ffff:127.0.0.1 — plain isLoopbackAddress() on the Inet6Address misses this
        InetAddress mapped = InetAddress.getByName("::ffff:127.0.0.1");
        assertTrue(ActuatorProbeService.isForbiddenAddress(mapped));
    }

    @Test
    @DisplayName("plain private IPv4 is forbidden")
    void privateIpv4Forbidden() throws Exception {
        assertTrue(ActuatorProbeService.isForbiddenAddress(InetAddress.getByName("10.0.0.5")));
        assertTrue(ActuatorProbeService.isForbiddenAddress(InetAddress.getByName("192.168.1.10")));
        assertTrue(ActuatorProbeService.isForbiddenAddress(InetAddress.getByName("127.0.0.1")));
    }

    @Test
    @DisplayName("public address is allowed")
    void publicAddressAllowed() throws Exception {
        assertFalse(ActuatorProbeService.isForbiddenAddress(InetAddress.getByName("8.8.8.8")));
    }
}
