package org.example.temporal.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.temporal.api.common.v1.Payload;
import io.temporal.common.converter.JacksonJsonPayloadConverter;
import io.temporal.common.converter.PayloadConverter;
import io.temporal.payload.context.SerializationContext;

import java.lang.reflect.Type;
import java.util.Optional;

public final class ContextAwareJacksonPayloadConverter extends JacksonJsonPayloadConverter {
    private final ObjectMapper mapper;
    private final SerializationContext context;

    public ContextAwareJacksonPayloadConverter(ObjectMapper mapper) {
        this(mapper, null);
    }

    private ContextAwareJacksonPayloadConverter(
            ObjectMapper mapper,
            SerializationContext context
    ) {
        super(mapper);
        this.mapper = mapper;
        this.context = context;
    }

    @Override
    public Optional<Payload> toData(Object value) {
        return TemporalSerializationContextHolder.withContext(context, () -> super.toData(value));
    }

    @Override
    public <T> T fromData(Payload content, Class<T> valueClass, Type valueType) {
        return TemporalSerializationContextHolder.withContext(
                context,
                () -> super.fromData(content, valueClass, valueType)
        );
    }

    @Override
    public PayloadConverter withContext(SerializationContext context) {
        return new ContextAwareJacksonPayloadConverter(mapper, context);
    }
}
