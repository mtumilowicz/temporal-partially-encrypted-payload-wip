package org.example.temporal;

import io.quarkiverse.temporal.TemporalActivity;
import io.temporal.activity.Activity;
import jakarta.inject.Inject;
import org.example.temporal.codec.SecureString;

import java.util.UUID;

@TemporalActivity(workers = "<default>")
public class ApiKeyActivityImpl implements ApiKeyActivity {

    @Inject
    UtcTimestampProvider utcTimestampProvider;

    @Override
    public RotateResult rotateApiKey(SecureString oldApiKey) {
        SecureString newApiKey = new SecureString(
                "sk_new_hardcoded_123".toCharArray(),
                UUID.fromString(Activity.getExecutionContext().getInfo().getWorkflowId())
        );
        String date = utcTimestampProvider.nowIsoMillis();
        return new RotateResult(oldApiKey, newApiKey, date);
    }
}
