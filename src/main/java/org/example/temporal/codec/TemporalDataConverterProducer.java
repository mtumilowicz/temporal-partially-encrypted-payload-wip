package org.example.temporal.codec;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.DefaultDataConverter;
import io.temporal.common.converter.JacksonJsonPayloadConverter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.example.security.PartialPayloadCrypto;

import java.io.IOException;

@ApplicationScoped
public class TemporalDataConverterProducer {

    private static final String TOKEN_PREFIX = "enc:v1:";

    @Produces
    @Singleton
    DataConverter temporalDataConverter(PartialPayloadCrypto crypto) {
        ObjectMapper mapper = JacksonJsonPayloadConverter.newDefaultObjectMapper();

        SimpleModule sensitiveModule = new SimpleModule("temporal-sensitive-field-encryption");
        sensitiveModule.addSerializer(SensitiveString.class, new JsonSerializer<>() {
            @Override
            public void serialize(SensitiveString value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                if (value == null || value.value() == null) {
                    gen.writeNull();
                    return;
                }
                String encrypted = crypto.encrypt(value.value());
                if (encrypted == null) {
                    gen.writeNull();
                    return;
                }
                gen.writeString(encrypted);
            }
        });
        sensitiveModule.addDeserializer(SensitiveString.class, new JsonDeserializer<>() {
            @Override
            public SensitiveString deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String raw = p.getValueAsString();
                if (raw == null) {
                    return new SensitiveString(null);
                }
                if (raw.startsWith(TOKEN_PREFIX)) {
                    return new SensitiveString(crypto.decrypt(raw));
                }
                return new SensitiveString(raw);
            }
        });
        mapper.registerModule(sensitiveModule);

        return DefaultDataConverter.newDefaultInstance()
                .withPayloadConverterOverrides(new JacksonJsonPayloadConverter(mapper));
    }
}
