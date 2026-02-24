package org.example.temporal;

import org.example.temporal.codec.SecureString;

import java.util.Map;

public record ExampleWorkflowInput(
        String name,
        SecureString apiKey,
        Map<String, Object> parameters
) {
}
