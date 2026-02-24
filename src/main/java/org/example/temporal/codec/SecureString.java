package org.example.temporal.codec;

import java.util.Arrays;

public final class SecureString {
    private final char[] value;

    public SecureString(char[] value) {
        this.value = Arrays.copyOf(value, value.length);
    }

    public char[] chars() {
        return Arrays.copyOf(value, value.length);
    }

    public String asString() {
        return new String(value);
    }

    @Override
    public String toString() {
        return "SecureString(**redacted**)";
    }
}
