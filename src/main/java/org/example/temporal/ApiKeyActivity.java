package org.example.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import org.example.temporal.codec.SecureString;

@ActivityInterface
public interface ApiKeyActivity {

    @ActivityMethod
    RotateResult rotateApiKey(SecureString oldApiKey);
}
