package org.example.temporal;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class UtcTimestampProvider {

    public String nowIsoMillis() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
    }
}
