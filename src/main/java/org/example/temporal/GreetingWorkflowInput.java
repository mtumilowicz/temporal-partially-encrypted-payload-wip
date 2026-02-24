package org.example.temporal;

import org.example.temporal.codec.SensitiveString;

public record GreetingWorkflowInput(
        String name,
        SensitiveString apiKey
) {
}
