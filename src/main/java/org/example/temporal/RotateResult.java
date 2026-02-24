package org.example.temporal;

import org.example.temporal.codec.SensitiveString;

public record RotateResult(
        SensitiveString oldApiKey,
        SensitiveString newApiKey
) {
}
