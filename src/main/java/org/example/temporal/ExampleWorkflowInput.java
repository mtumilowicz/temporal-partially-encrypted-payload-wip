package org.example.temporal;

import org.example.temporal.codec.SecureString;

public record ExampleWorkflowInput(
        String name,
        SecureString apiKey
) {
}
