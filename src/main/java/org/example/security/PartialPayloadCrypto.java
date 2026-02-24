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

@ApplicationScoped
public class PartialPayloadCrypto {

    private static final String PREFIX = "enc:v1:";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final byte[] EMPTY_AAD = new byte[0];

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

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        return encrypt(plainText.toCharArray());
    }

    public String encrypt(char[] plainChars) {
        if (plainChars == null || plainChars.length == 0) {
            return null;
        }
        try {
            byte[] plainBytes = new String(plainChars).getBytes(StandardCharsets.UTF_8);
            byte[] cipherText = aead.encrypt(plainBytes, EMPTY_AAD);
            Arrays.fill(plainBytes, (byte) 0);
            return PREFIX + Base64.getEncoder().encodeToString(cipherText);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt payload field", e);
        }
    }

    public String decrypt(String token) {
        char[] plain = decryptToChars(token);
        return plain == null ? null : new String(plain);
    }

    public char[] decryptToChars(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (!token.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Encrypted field has unexpected format");
        }
        try {
            byte[] cipherText = Base64.getDecoder().decode(token.substring(PREFIX.length()));
            byte[] plainBytes = aead.decrypt(cipherText, EMPTY_AAD);
            char[] chars = new String(plainBytes, StandardCharsets.UTF_8).toCharArray();
            Arrays.fill(plainBytes, (byte) 0);
            return chars;
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt payload field", e);
        }
    }
}
