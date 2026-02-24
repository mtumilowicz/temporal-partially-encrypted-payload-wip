package org.example.temporal;

import io.quarkiverse.temporal.TemporalActivity;
import org.example.temporal.codec.SensitiveString;

@TemporalActivity(workers = "<default>")
public class ApiKeyActivityImpl implements ApiKeyActivity {

    @Override
    public ApiKeyProcessingResult rotateApiKey(SensitiveString oldApiKey) {
        SensitiveString newApiKey = new SensitiveString("sk_new_hardcoded_123");
        return new ApiKeyProcessingResult(oldApiKey, newApiKey);
    }
}
