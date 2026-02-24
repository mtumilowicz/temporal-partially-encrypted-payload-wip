package org.example.security;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@ApplicationScoped
public class PartialPayloadCrypto {

    private static final String PREFIX = "enc:v1:";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public PartialPayloadCrypto(@ConfigProperty(name = "app.crypto.key-base64") String keyBase64) {
        byte[] rawKey = Base64.getDecoder().decode(keyBase64);
        if (rawKey.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("app.crypto.key-base64 must decode to exactly 32 bytes");
        }
        this.key = new SecretKeySpec(rawKey, "AES");
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            return PREFIX + Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(cipherText);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt payload field", e);
        }
    }

    public String decrypt(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (!token.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Encrypted field has unexpected format");
        }
        String body = token.substring(PREFIX.length());
        String[] parts = body.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Encrypted field has unexpected format");
        }

        try {
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] cipherText = Base64.getDecoder().decode(parts[1]);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt payload field", e);
        }
    }
}
