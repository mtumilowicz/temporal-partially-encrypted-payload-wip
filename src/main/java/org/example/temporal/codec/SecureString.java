package org.example.temporal.codec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.errorprone.annotations.RestrictedApi;
import org.example.security.AllowUnsafeChars;

import java.util.Arrays;
import java.util.Objects;

public final class SecureString {
    private final char[] value;

    public SecureString(char[] value) {
        this.value = Arrays.copyOf(value, value.length);
    }

    @JsonCreator
    public static SecureString fromPlainText(String value) {
        Objects.requireNonNull(value, "value must not be null");
        return new SecureString(value.toCharArray());
    }

    @RestrictedApi(
            explanation = "Access to secret chars must be explicitly acknowledged via @AllowUnsafeChars",
            link = "",
            allowlistAnnotations = {AllowUnsafeChars.class}
    )
    public char[] unsafeChars() {
        return Arrays.copyOf(value, value.length);
    }

    @Override
    public String toString() {
        return "SecureString(**redacted**)";
    }
}
