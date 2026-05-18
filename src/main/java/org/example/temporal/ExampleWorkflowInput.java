package org.example.temporal;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.example.temporal.codec.SecureString;
import org.example.temporal.codec.TemporalSecretParametersDeserializer;

import java.util.Map;

public record ExampleWorkflowInput(
        String name,
        SecureString apiKey,
        @JsonDeserialize(using = TemporalSecretParametersDeserializer.class)
        Map<String, Object> parameters
) {
}
