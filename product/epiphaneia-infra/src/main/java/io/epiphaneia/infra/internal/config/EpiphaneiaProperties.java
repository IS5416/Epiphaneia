package io.epiphaneia.infra.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Epiphaneia infrastructure configuration properties.
 * <p>
 * The encryption key is validated at service construction time in
 * {@link io.epiphaneia.infra.internal.crypto.AesGcmEncryptionService}.
 * <p>
 * {@link #toString()} deliberately masks the key to prevent credential leaks
 * through logging or stack traces.
 */
@ConfigurationProperties(prefix = "epiphaneia")
public record EpiphaneiaProperties(String encryptionKey) {

    @Override
    public String toString() {
        return "EpiphaneiaProperties[encryptionKey=****]";
    }
}
