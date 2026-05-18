package org.example.temporal.codec;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TemporalSecretParametersDeserializer extends JsonDeserializer<Map<String, Object>> {
    @Override
    public Map<String, Object> deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        ObjectCodec codec = parser.getCodec();
        JsonNode root = codec.readTree(parser);
        if (root == null || root.isNull()) {
            return null;
        }
        if (!root.isObject()) {
            return context.reportInputMismatch(Map.class, "parameters must be a JSON object");
        }

        Map<String, Object> parameters = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> property : root.properties()) {
            String key = property.getKey();
            JsonNode value = property.getValue();

            if (key.startsWith("secret") && value.isTextual()) {
                parameters.put(key, codec.treeToValue(value, SecureString.class));
            } else {
                parameters.put(key, codec.treeToValue(value, Object.class));
            }
        }
        return parameters;
    }
}
