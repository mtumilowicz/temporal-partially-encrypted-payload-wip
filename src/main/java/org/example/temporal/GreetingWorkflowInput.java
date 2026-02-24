package org.example.temporal;

import org.example.temporal.codec.SensitiveString;

public record GreetingWorkflowInput(
        String name,
        int repeatCount,
        SensitiveString apiKey,
        boolean includeSensitiveOutput
) {
}
