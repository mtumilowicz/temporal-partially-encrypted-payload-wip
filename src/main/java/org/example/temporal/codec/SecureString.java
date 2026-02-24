package org.example.temporal.codec;

public record SecureString(String value) {
    @Override
    public String toString() {
        return "SecureString(**redacted**)";
    }
}
