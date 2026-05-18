package org.example.temporal.codec;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class SecretParametersDeserializer extends JsonDeserializer<Map<String, Object>> {
    private static final String TOKEN_PREFIX = "enc:v1:";

    @Override
    public Map<String, Object> deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        ObjectCodec codec = parser.getCodec();
        JsonNode root = codec.readTree(parser);
        if (!root.isObject()) {
            return context.reportInputMismatch(Map.class, "parameters must be a JSON object");
        }

        Map<String, Object> parameters = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> property : root.properties()) {
            String key = property.getKey();
            JsonNode value = property.getValue();

            if (key.startsWith("secret")) {
                parameters.put(key, secureString(value, codec));
            } else {
                parameters.put(key, codec.treeToValue(value, Object.class));
            }
        }
        return parameters;
    }

    protected SecureString secureString(JsonNode value, ObjectCodec codec) {
        if (!value.isTextual()) {
            throw new IllegalArgumentException("secret parameters must be strings");
        }
        if (value.textValue().startsWith(TOKEN_PREFIX)) {
            try {
                return codec.treeToValue(value, SecureString.class);
            } catch (IOException e) {
                throw new IllegalArgumentException("encrypted secret parameter is invalid", e);
            }
        }
        return new SecureString(value.textValue().toCharArray());
    }
}
