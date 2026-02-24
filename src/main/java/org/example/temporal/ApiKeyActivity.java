package org.example.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import org.example.temporal.codec.SensitiveString;

@ActivityInterface
public interface ApiKeyActivity {

    @ActivityMethod
    ApiKeyProcessingResult rotateApiKey(SensitiveString oldApiKey);
}
