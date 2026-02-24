package org.example.temporal;

import io.quarkiverse.temporal.TemporalActivity;
import org.example.temporal.codec.SensitiveString;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@TemporalActivity(workers = "<default>")
public class ApiKeyActivityImpl implements ApiKeyActivity {

    @Override
    public ApiKeyProcessingResult processApiKey(SensitiveString apiKey) {
        String plainApiKey = apiKey.value();
        String fingerprint = fingerprint(plainApiKey);
        String sensitiveOutput = "apiKey.last4=" + last4(plainApiKey) + ";apiKey.length=" + plainApiKey.length();

        return new ApiKeyProcessingResult(fingerprint, new SensitiveString(sensitiveOutput));
    }

    private static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return toHex(digest, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toHex(byte[] bytes, int maxChars) {
        char[] hexArray = "0123456789abcdef".toCharArray();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xFF;
            sb.append(hexArray[v >>> 4]).append(hexArray[v & 0x0F]);
            if (sb.length() >= maxChars) {
                return sb.substring(0, maxChars);
            }
        }
        return sb.toString();
    }

    private static String last4(String value) {
        if (value.length() <= 4) {
            return value;
        }
        return value.substring(value.length() - 4);
    }
}
