package io.epiphaneia.infra.api;

/** Service for encrypting/decrypting sensitive data at rest. */
public interface EncryptionService {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
