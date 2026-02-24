package org.example.security;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.subtle.AesGcmJce;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.UUID;

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

    public String encryptFromChars(char[] plainChars, UUID workflowId) {
        try {
            byte[] plainBytes = new String(plainChars).getBytes(StandardCharsets.UTF_8);
            byte[] aad = workflowId.toString().getBytes(StandardCharsets.UTF_8);
            byte[] cipherText = aead.encrypt(plainBytes, aad);
            return PREFIX + Base64.getEncoder().encodeToString(cipherText);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt payload field", e);
        }
    }

    public char[] decryptToChars(String token, UUID workflowId) {
        if (!token.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Encrypted field has unexpected format");
        }
        try {
            byte[] cipherText = Base64.getDecoder().decode(token.substring(PREFIX.length()));
            byte[] aad = workflowId.toString().getBytes(StandardCharsets.UTF_8);
            byte[] plainBytes = aead.decrypt(cipherText, aad);
            return new String(plainBytes, StandardCharsets.UTF_8).toCharArray();
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt payload field", e);
        }
    }

    public boolean isEncryptedToken(String token) {
        return token != null && token.startsWith(PREFIX);
    }
}
