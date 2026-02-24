package org.example.temporal;

import org.example.temporal.codec.SensitiveString;

public record GreetingWorkflowOutput(
        String output,
        String apiKeyFingerprint,
        SensitiveString sensitiveOutputPart
) {
}
