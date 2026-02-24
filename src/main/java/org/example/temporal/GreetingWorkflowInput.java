package org.example.temporal;

import org.example.temporal.codec.SecureString;

public record GreetingWorkflowInput(
        String name,
        SecureString apiKey
) {
}
