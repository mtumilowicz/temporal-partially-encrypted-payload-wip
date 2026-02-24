package org.example.temporal;

import org.example.temporal.codec.SensitiveString;

public record GreetingWorkflowOutput(
        SensitiveString oldApiKey,
        SensitiveString newApiKey
) {
}
