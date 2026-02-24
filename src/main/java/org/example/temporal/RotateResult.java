package org.example.temporal;

import org.example.temporal.codec.SecureString;

public record RotateResult(
        SecureString oldApiKey,
        SecureString newApiKey,
        String date
) {
}
