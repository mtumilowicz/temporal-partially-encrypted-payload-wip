package org.example.temporal;

import io.quarkiverse.temporal.TemporalActivity;
import jakarta.inject.Inject;
import org.example.security.AllowUnsafeChars;
import org.example.temporal.codec.SecureString;

import java.util.Arrays;
import java.util.Map;

public class ApiKeyActivityImpl implements ApiKeyActivity {

    @Inject
    UtcTimestampProvider utcTimestampProvider;

    @Override
    public RotateResult rotateApiKey(SecureString oldApiKey) {
        SecureString newApiKey = rotatedApiKey(oldApiKey);
        String date = utcTimestampProvider.nowIsoMillis();
        return new RotateResult(oldApiKey, newApiKey, date);
    }

    @Override
    public String encryptedApiKeyFromParameters(Map<String, Object> parameters) {
        return (String) parameters.get("secretApiKey");
    }

    private static SecureString rotatedApiKey(SecureString oldApiKey) {
        @AllowUnsafeChars("building rotated API key from existing API key")
        char[] oldChars = oldApiKey.unsafeChars();
        char[] suffix = "_rotated".toCharArray();
        char[] rotatedChars = Arrays.copyOf(oldChars, oldChars.length + suffix.length);
        try {
            System.arraycopy(suffix, 0, rotatedChars, oldChars.length, suffix.length);
            return new SecureString(rotatedChars);
        } finally {
            Arrays.fill(oldChars, '\0');
            Arrays.fill(suffix, '\0');
            Arrays.fill(rotatedChars, '\0');
        }
    }
}
