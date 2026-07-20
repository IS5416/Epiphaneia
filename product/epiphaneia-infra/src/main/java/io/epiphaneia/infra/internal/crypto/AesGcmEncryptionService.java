package io.epiphaneia.infra.internal.crypto;

import io.epiphaneia.infra.api.EncryptionService;
import io.epiphaneia.infra.internal.config.EpiphaneiaProperties;
import io.epiphaneia.infra.internal.exception.InvalidConfigurationException;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AES-256-GCM encryption for sensitive data at rest.
 * <p>
 * IV (12 bytes) is generated per-encryption and prepended to the ciphertext.
 * The encryption key must be a 64-character hex string (256 bits) provided via
 * the EPIPHANEIA_ENCRYPTION_KEY environment variable.
 * Generate with: {@code openssl rand -hex 32}
 */
@Service
public class AesGcmEncryptionService implements EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecretKey key;

    public AesGcmEncryptionService(EpiphaneiaProperties properties) {
        byte[] keyBytes;
        try {
            keyBytes = HexFormat.of().parseHex(properties.encryptionKey());
        } catch (IllegalArgumentException e) {
            throw new InvalidConfigurationException(
                    "EPIPHANEIA_ENCRYPTION_KEY must be a 64-character hex string. " + e.getMessage());
        }
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new InvalidConfigurationException(
                    "EPIPHANEIA_ENCRYPTION_KEY must decode to 32 bytes (256 bits), got " + keyBytes.length);
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer combined = ByteBuffer.allocate(iv.length + ciphertext.length);
            combined.put(iv);
            combined.put(ciphertext);
            return Base64.getEncoder().encodeToString(combined.array());
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            throw new IllegalArgumentException("ciphertext must not be null");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            if (combined.length < IV_LENGTH + 1) {
                throw new IllegalArgumentException("Ciphertext too short for AES-GCM");
            }
            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
