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
import io.temporal.payload.context.HasWorkflowSerializationContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.example.security.AllowUnsafeChars;
import org.example.security.PartialPayloadCrypto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@ApplicationScoped
public class TemporalDataConverterProducer {

    @Produces
    @Singleton
    DataConverter temporalDataConverter(PartialPayloadCrypto crypto) {
        ObjectMapper mapper = JacksonJsonPayloadConverter.newDefaultObjectMapper();

        SimpleModule secureModule = new SimpleModule("temporal-secure-field-encryption");
        secureModule.addSerializer(SecureString.class, new SecureStringSerializer(crypto));
        secureModule.addDeserializer(SecureString.class, new EncryptedSecureStringDeserializer(crypto));
        mapper.registerModule(secureModule);

        return DefaultDataConverter.newDefaultInstance()
                .withPayloadConverterOverrides(
                        new ContextAwareJacksonPayloadConverter(mapper)
                );
    }

    private static String encryptSecureString(PartialPayloadCrypto crypto, SecureString value, byte[] aad) {
        @AllowUnsafeChars("encrypting secure value before Temporal payload serialization")
        char[] chars = value.unsafeChars();
        try {
            return crypto.encryptFromChars(chars, aad);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    private static byte[] buildAad() {
        HasWorkflowSerializationContext workflowContext =
                TemporalSerializationContextHolder.getWorkflowContextOrThrow();

        String namespace = workflowContext.getNamespace();
        if (namespace.isBlank()) {
            throw new IllegalStateException("namespace is missing in Temporal serialization context");
        }

        String workflowId = workflowContext.getWorkflowId();
        if (workflowId.isBlank()) {
            throw new IllegalStateException("workflowId is missing in Temporal serialization context");
        }

        return ("ns=" + namespace + "\nwid=" + workflowId).getBytes(StandardCharsets.UTF_8);
    }

    private static final class SecureStringSerializer extends JsonSerializer<SecureString> {
        private final PartialPayloadCrypto crypto;

        private SecureStringSerializer(PartialPayloadCrypto crypto) {
            this.crypto = crypto;
        }

        @Override
        public void serialize(SecureString value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(encryptSecureString(crypto, value, buildAad()));
        }
    }

    private static final class EncryptedSecureStringDeserializer extends JsonDeserializer<SecureString> {
        private final PartialPayloadCrypto crypto;

        private EncryptedSecureStringDeserializer(PartialPayloadCrypto crypto) {
            this.crypto = crypto;
        }

        @Override
        public SecureString deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String raw = p.getValueAsString();
            if (raw == null) {
                throw new IllegalArgumentException("SecureString payload is null");
            }
            return new SecureString(crypto.decryptToChars(raw, buildAad()));
        }
    }
}
