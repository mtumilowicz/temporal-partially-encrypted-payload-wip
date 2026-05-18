package org.example.temporal;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.example.temporal.codec.SecretParametersDeserializer;
import org.example.temporal.codec.SecureString;

import java.util.Map;

public record ExampleWorkflowInput(
        String name,
        SecureString apiKey,
        @JsonDeserialize(using = SecretParametersDeserializer.class)
        Map<String, Object> parameters
) {
}
