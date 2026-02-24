package org.example.temporal.codec;

import io.temporal.payload.context.HasWorkflowSerializationContext;
import io.temporal.payload.context.SerializationContext;

import java.util.function.Supplier;

public final class TemporalSerializationContextHolder {
    private static final ThreadLocal<SerializationContext> CTX = new ThreadLocal<>();

    private TemporalSerializationContextHolder() {
    }

    public static HasWorkflowSerializationContext getWorkflowContextOrThrow() {
        SerializationContext context = CTX.get();
        if (!(context instanceof HasWorkflowSerializationContext workflowContext)) {
            throw new IllegalStateException("Temporal serialization context with workflowId is required");
        }
        return workflowContext;
    }

    public static <T> T withContext(SerializationContext context, Supplier<T> action) {
        CTX.set(context);
        try {
            return action.get();
        } finally {
            CTX.remove();
        }
    }
}
