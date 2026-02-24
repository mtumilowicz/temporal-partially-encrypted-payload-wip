package org.example.temporal.codec;

public record SensitiveString(String value) {
    @Override
    public String toString() {
        return "SensitiveString(**redacted**)";
    }
}
