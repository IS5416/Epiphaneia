package io.epiphaneia.infra.api.connector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthConfigTest {

    @Test
    @DisplayName("BASIC auth parsed from persisted JSON")
    void parsesBasicAuth() {
        AuthConfig auth = AuthConfig.from("BASIC",
                "{\"username\":\"svc\",\"password\":\"secret\"}");
        assertEquals(AuthConfig.Type.BASIC, auth.type());
        assertEquals("svc", auth.username());
        assertEquals("secret", auth.password());
    }

    @Test
    @DisplayName("BEARER auth parsed from persisted JSON")
    void parsesBearerAuth() {
        AuthConfig auth = AuthConfig.from("BEARER", "{\"token\":\"abc123\"}");
        assertEquals(AuthConfig.Type.BEARER, auth.type());
        assertEquals("abc123", auth.token());
    }

    @Test
    @DisplayName("unknown auth type degrades to NONE instead of failing")
    void unknownTypeDegradesToNone() {
        AuthConfig auth = AuthConfig.from("OAUTH", "{\"token\":\"x\"}");
        assertEquals(AuthConfig.Type.NONE, auth.type());
        assertNull(auth.token());
    }

    @Test
    @DisplayName("malformed JSON degrades to NONE without throwing")
    void malformedJsonDegradesToNone() {
        AuthConfig auth = AuthConfig.from("BASIC", "not-json{{");
        assertEquals(AuthConfig.Type.BASIC, auth.type());
        assertNull(auth.username());
        assertNull(auth.password());
    }

    @Test
    @DisplayName("null/blank config yields typed auth with null credentials")
    void blankConfigYieldsTypedAuth() {
        AuthConfig auth = AuthConfig.from("BASIC", null);
        assertEquals(AuthConfig.Type.BASIC, auth.type());
        assertNull(auth.username());

        AuthConfig none = AuthConfig.from(null, null);
        assertEquals(AuthConfig.Type.NONE, none.type());
    }
}
