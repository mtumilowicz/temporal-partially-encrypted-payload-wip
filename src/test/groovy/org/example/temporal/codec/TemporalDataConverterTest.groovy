package org.example.temporal.codec

import com.fasterxml.jackson.databind.ObjectMapper
import io.temporal.api.common.v1.Payloads
import io.temporal.common.converter.DataConverter
import org.example.security.AllowUnsafeChars
import org.example.security.PartialPayloadCrypto
import org.example.temporal.ExampleWorkflowInput
import spock.lang.Specification

import java.util.Arrays
import java.util.Optional

class TemporalDataConverterTest extends Specification {

    private static final String ENCRYPTED_PREFIX = 'enc:v1:'
    private static final String TEST_KEY_BASE64 = 'MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY='

    private final ObjectMapper objectMapper = new ObjectMapper()
    private final DataConverter dataConverter = new TemporalDataConverterProducer()
            .temporalDataConverter(new PartialPayloadCrypto(TEST_KEY_BASE64))

    def 'serializes secure fields as encrypted payload values and deserializes back to original chars'() {
        given:
        String plainSecret = 'sk_test_roundtrip_secret'
        String plainName = 'Temporal'
        ExampleWorkflowInput input = new ExampleWorkflowInput(
                plainName,
                new SecureString(plainSecret.toCharArray())
        )

        when:
        Payloads payloads = dataConverter.toPayloads(input).orElseThrow()
        SerializedExampleWorkflowInput payload = payloadAsInput(payloads)

        then:
        payload.name == plainName
        payload.apiKey.startsWith(ENCRYPTED_PREFIX)
        !payload.apiKey.contains(plainSecret)

        when:
        ExampleWorkflowInput decoded = dataConverter.fromPayloads(
                0,
                Optional.of(payloads),
                ExampleWorkflowInput.class,
                ExampleWorkflowInput.class
        )

        then:
        @AllowUnsafeChars('test verification that converter round-trip restores secret')
        char[] chars = decoded.apiKey().unsafeChars()
        try {
            Arrays.equals(plainSecret.toCharArray(), chars)
        } finally {
            Arrays.fill(chars, (char) 0)
        }
    }

    def 'keeps non-secure fields in plaintext while encrypting secure fields'() {
        given:
        String plainSecret = 'sk_test_nonsecure_check'
        String plainName = 'VisibleName'
        ExampleWorkflowInput input = new ExampleWorkflowInput(
                plainName,
                new SecureString(plainSecret.toCharArray())
        )

        when:
        Payloads payloads = dataConverter.toPayloads(input).orElseThrow()
        SerializedExampleWorkflowInput payload = payloadAsInput(payloads)

        then:
        payload.name == plainName
        payload.apiKey != null
        payload.apiKey.startsWith(ENCRYPTED_PREFIX)
        !payload.apiKey.contains(plainSecret)
    }

    private SerializedExampleWorkflowInput payloadAsInput(Payloads payloads) {
        String json = payloads.getPayloads(0).getData().toStringUtf8()
        return objectMapper.readValue(json, SerializedExampleWorkflowInput.class)
    }

    private static class SerializedExampleWorkflowInput {
        String name
        String apiKey
    }
}
