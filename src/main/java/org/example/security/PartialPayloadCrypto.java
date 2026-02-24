package org.example.security;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.subtle.AesGcmJce;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

@ApplicationScoped
public class PartialPayloadCrypto {

    private static final String PREFIX = "enc:v1:";
    private static final int KEY_LENGTH_BYTES = 32;

    private final Aead aead;

    public PartialPayloadCrypto(@ConfigProperty(name = "app.crypto.key-base64") String keyBase64) {
        try {
            byte[] rawKey = Base64.getDecoder().decode(keyBase64);
            if (rawKey.length != KEY_LENGTH_BYTES) {
                throw new IllegalStateException("app.crypto.key-base64 must decode to exactly 32 bytes");
            }

            AeadConfig.register();
            this.aead = new AesGcmJce(rawKey);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to initialize Tink AEAD", e);
        }
    }

    public String encryptFromChars(char[] plainChars, byte[] aad) {
        try {
            validateAad(aad);
            byte[] plainBytes = new String(plainChars).getBytes(StandardCharsets.UTF_8);
            byte[] cipherText = aead.encrypt(plainBytes, aad);
            Arrays.fill(plainBytes, (byte) 0);
            return PREFIX + Base64.getEncoder().encodeToString(cipherText);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt payload field", e);
        }
    }

    public char[] decryptToChars(String token, byte[] aad) {
        if (!token.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Encrypted field has unexpected format");
        }
        try {
            validateAad(aad);
            byte[] cipherText = Base64.getDecoder().decode(token.substring(PREFIX.length()));
            byte[] plainBytes = aead.decrypt(cipherText, aad);
            char[] chars = new String(plainBytes, StandardCharsets.UTF_8).toCharArray();
            Arrays.fill(plainBytes, (byte) 0);
            return chars;
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt payload field", e);
        }
    }

    private static void validateAad(byte[] aad) {
        Objects.requireNonNull(aad, "AAD must not be null");
        if (aad.length == 0) {
            throw new IllegalArgumentException("AAD must not be empty");
        }
    }
}
