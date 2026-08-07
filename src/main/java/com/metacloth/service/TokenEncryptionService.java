package com.metacloth.service;

import com.metacloth.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class TokenEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenEncryptionService(AppProperties properties) {
        String encoded = properties.google().tokenEncryptionKey();

        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(
                    "GOOGLE_TOKEN_ENCRYPTION_KEY must be configured."
            );
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);

            if (decoded.length != 32) {
                throw new IllegalStateException(
                        "GOOGLE_TOKEN_ENCRYPTION_KEY must decode to exactly 32 bytes."
                );
            }

            this.key = new SecretKeySpec(decoded, "AES");

        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "GOOGLE_TOKEN_ENCRYPTION_KEY must be valid Base64.",
                    ex
            );
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_LENGTH, iv)
            );

            byte[] encrypted =
                    cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];

            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(
                    encrypted,
                    0,
                    combined,
                    iv.length,
                    encrypted.length
            );

            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to encrypt Google OAuth token.",
                    ex
            );
        }
    }

    public String decrypt(String encryptedValue) {
        if (encryptedValue == null) {
            return null;
        }

        try {
            byte[] combined =
                    Base64.getDecoder().decode(encryptedValue);

            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted token.");
            }

            byte[] iv = new byte[IV_LENGTH];

            byte[] ciphertext =
                    new byte[combined.length - IV_LENGTH];

            System.arraycopy(
                    combined,
                    0,
                    iv,
                    0,
                    IV_LENGTH
            );

            System.arraycopy(
                    combined,
                    IV_LENGTH,
                    ciphertext,
                    0,
                    ciphertext.length
            );

            Cipher cipher = Cipher.getInstance(ALGORITHM);

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_LENGTH, iv)
            );

            byte[] decrypted = cipher.doFinal(ciphertext);

            return new String(
                    decrypted,
                    StandardCharsets.UTF_8
            );

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to decrypt Google OAuth token.",
                    ex
            );
        }
    }
}