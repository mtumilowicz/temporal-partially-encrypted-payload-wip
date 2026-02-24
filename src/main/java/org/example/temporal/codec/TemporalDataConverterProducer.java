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
import org.example.security.AllowUnsafeChars;
import org.example.security.PartialPayloadCrypto;

import java.io.IOException;
import java.util.Arrays;

@ApplicationScoped
public class TemporalDataConverterProducer {

    private static final String TOKEN_PREFIX = "enc:v1:";

    @Produces
    @Singleton
    DataConverter temporalDataConverter(PartialPayloadCrypto crypto) {
        ObjectMapper mapper = JacksonJsonPayloadConverter.newDefaultObjectMapper();

        SimpleModule secureModule = new SimpleModule("temporal-secure-field-encryption");
        secureModule.addSerializer(SecureString.class, new JsonSerializer<>() {
            @Override
            public void serialize(SecureString value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                String encrypted = encryptSecureString(crypto, value);
                gen.writeString(encrypted);
            }
        });
        secureModule.addDeserializer(SecureString.class, new JsonDeserializer<>() {
            @Override
            public SecureString deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String raw = p.getValueAsString();
                if (raw.startsWith(TOKEN_PREFIX)) {
                    return new SecureString(crypto.decryptToChars(raw));
                }
                return new SecureString(raw.toCharArray());
            }
        });
        mapper.registerModule(secureModule);

        return DefaultDataConverter.newDefaultInstance()
                .withPayloadConverterOverrides(new JacksonJsonPayloadConverter(mapper));
    }

    private static String encryptSecureString(PartialPayloadCrypto crypto, SecureString value) {
        @AllowUnsafeChars("encrypting secure value before Temporal payload serialization")
        char[] chars = value.unsafeChars();
        try {
            return crypto.encrypt(chars);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }
}
