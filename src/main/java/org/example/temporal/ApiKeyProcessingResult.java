package org.example.temporal;

import org.example.temporal.codec.SensitiveString;

public record ApiKeyProcessingResult(
        SensitiveString oldApiKey,
        SensitiveString newApiKey
) {
}
