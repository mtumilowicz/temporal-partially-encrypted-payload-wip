package org.example.temporal.codec;

import com.google.errorprone.annotations.RestrictedApi;
import org.example.security.AllowUnsafeChars;

import java.util.Arrays;
import java.util.UUID;

public final class SecureString {
    private final char[] value;
    private final UUID workflowId;

    public SecureString(char[] value, UUID workflowId) {
        this.value = Arrays.copyOf(value, value.length);
        this.workflowId = workflowId;
    }

    @RestrictedApi(
            explanation = "Access to secret chars must be explicitly acknowledged via @AllowUnsafeChars",
            link = "",
            allowlistAnnotations = {AllowUnsafeChars.class}
    )
    public char[] unsafeChars() {
        return Arrays.copyOf(value, value.length);
    }

    public UUID workflowId() {
        return workflowId;
    }

    @Override
    public String toString() {
        return "SecureString(**redacted**)";
    }
}
