package org.example.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import org.example.temporal.codec.SecureString;

import java.util.Map;

@ActivityInterface
public interface ApiKeyActivity {

    @ActivityMethod
    RotateResult rotateApiKey(SecureString oldApiKey);

    @ActivityMethod
    String encryptedApiKeyFromParameters(Map<String, Object> parameters);
}
