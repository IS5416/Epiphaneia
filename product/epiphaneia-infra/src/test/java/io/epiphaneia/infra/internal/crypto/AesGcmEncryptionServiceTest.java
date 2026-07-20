package io.epiphaneia.infra.internal.crypto;

import io.epiphaneia.infra.internal.config.EpiphaneiaProperties;
import io.epiphaneia.infra.internal.exception.InvalidConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmEncryptionServiceTest {

    private static final String VALID_KEY = generateRandomHexKey();

    private static String generateRandomHexKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return HexFormat.of().formatHex(key);
    }

    @Test
    @DisplayName("round-trip: encrypt then decrypt returns original plaintext")
    void roundTrip() {
        var svc = new AesGcmEncryptionService(new EpiphaneiaProperties(VALID_KEY));
        String original = "sensitive-api-key-abc123";
        String encrypted = svc.encrypt(original);
        assertNotEquals(original, encrypted);
        assertEquals(original, svc.decrypt(encrypted));
    }

    @Test
    @DisplayName("encrypt produces different output each time (random IV)")
    void encryptIsNonDeterministic() {
        var svc = new AesGcmEncryptionService(new EpiphaneiaProperties(VALID_KEY));
        String plain = "test";
        String a = svc.encrypt(plain);
        String b = svc.encrypt(plain);
        assertNotEquals(a, b, "Same plaintext should produce different ciphertext due to random IV");
    }

    @Test
    @DisplayName("decrypt rejects tampered ciphertext")
    void tamperedCiphertextThrows() {
        var svc = new AesGcmEncryptionService(new EpiphaneiaProperties(VALID_KEY));
        String encrypted = svc.encrypt("secret");
        byte[] tampered = Base64.getDecoder().decode(encrypted);
        tampered[5] ^= 0x01; // flip a bit
        String bad = Base64.getEncoder().encodeToString(tampered);
        assertThrows(RuntimeException.class, () -> svc.decrypt(bad));
    }

    @Test
    @DisplayName("decrypt with wrong key throws")
    void wrongKeyThrows() {
        var svc1 = new AesGcmEncryptionService(new EpiphaneiaProperties(VALID_KEY));
        var svc2 = new AesGcmEncryptionService(new EpiphaneiaProperties(generateRandomHexKey()));
        String encrypted = svc1.encrypt("secret");
        assertThrows(RuntimeException.class, () -> svc2.decrypt(encrypted));
    }

    @Test
    @DisplayName("empty string round-trips correctly")
    void emptyString() {
        var svc = new AesGcmEncryptionService(new EpiphaneiaProperties(VALID_KEY));
        assertEquals("", svc.decrypt(svc.encrypt("")));
    }

    @Test
    @DisplayName("unicode text round-trips correctly")
    void unicodeText() {
        var svc = new AesGcmEncryptionService(new EpiphaneiaProperties(VALID_KEY));
        String original = "你好世界 🔥 テスト";
        assertEquals(original, svc.decrypt(svc.encrypt(original)));
    }

    @Test
    @DisplayName("rejects invalid hex key")
    void invalidHexKey() {
        assertThrows(InvalidConfigurationException.class,
                () -> new AesGcmEncryptionService(new EpiphaneiaProperties("not-hex!!")));
    }

    @Test
    @DisplayName("rejects key of wrong length")
    void wrongLengthKey() {
        String shortKey = "abcdef12"; // 4 bytes, not 32
        assertThrows(InvalidConfigurationException.class,
                () -> new AesGcmEncryptionService(new EpiphaneiaProperties(shortKey)));
    }

    @Test
    @DisplayName("encrypt null throws IllegalArgumentException")
    void encryptNull() {
        var svc = new AesGcmEncryptionService(new EpiphaneiaProperties(VALID_KEY));
        assertThrows(IllegalArgumentException.class, () -> svc.encrypt(null));
    }

    @Test
    @DisplayName("decrypt null throws IllegalArgumentException")
    void decryptNull() {
        var svc = new AesGcmEncryptionService(new EpiphaneiaProperties(VALID_KEY));
        assertThrows(IllegalArgumentException.class, () -> svc.decrypt(null));
    }
}
