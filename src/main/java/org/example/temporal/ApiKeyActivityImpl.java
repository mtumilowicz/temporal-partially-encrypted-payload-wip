package org.example.temporal;

import io.quarkiverse.temporal.TemporalActivity;
import org.example.temporal.codec.SecureString;

import java.time.LocalDate;
import java.time.ZoneOffset;

@TemporalActivity(workers = "<default>")
public class ApiKeyActivityImpl implements ApiKeyActivity {

    @Override
    public RotateResult rotateApiKey(SecureString oldApiKey) {
        SecureString newApiKey = new SecureString("sk_new_hardcoded_123");
        String date = LocalDate.now(ZoneOffset.UTC).toString();
        return new RotateResult(oldApiKey, newApiKey, date);
    }
}
