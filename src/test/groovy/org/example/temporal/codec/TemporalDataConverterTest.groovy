package org.example.temporal.codec

import com.fasterxml.jackson.databind.ObjectMapper
import io.temporal.api.common.v1.Payloads
import io.temporal.common.converter.DataConverter
import org.example.security.AllowUnsafeChars
import org.example.security.PartialPayloadCrypto
import org.example.temporal.GreetingWorkflowInput
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
        GreetingWorkflowInput input = new GreetingWorkflowInput(
                plainName,
                new SecureString(plainSecret.toCharArray())
        )

        when:
        Payloads payloads = dataConverter.toPayloads(input).orElseThrow()
        SerializedGreetingWorkflowInput payload = payloadAsInput(payloads)

        then:
        payload.name == plainName
        payload.apiKey.startsWith(ENCRYPTED_PREFIX)
        !payload.apiKey.contains(plainSecret)

        when:
        GreetingWorkflowInput decoded = dataConverter.fromPayloads(
                0,
                Optional.of(payloads),
                GreetingWorkflowInput.class,
                GreetingWorkflowInput.class
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
        GreetingWorkflowInput input = new GreetingWorkflowInput(
                plainName,
                new SecureString(plainSecret.toCharArray())
        )

        when:
        Payloads payloads = dataConverter.toPayloads(input).orElseThrow()
        SerializedGreetingWorkflowInput payload = payloadAsInput(payloads)

        then:
        payload.name == plainName
        payload.apiKey != null
        payload.apiKey.startsWith(ENCRYPTED_PREFIX)
        !payload.apiKey.contains(plainSecret)
    }

    private SerializedGreetingWorkflowInput payloadAsInput(Payloads payloads) {
        String json = payloads.getPayloads(0).getData().toStringUtf8()
        return objectMapper.readValue(json, SerializedGreetingWorkflowInput.class)
    }

    private static class SerializedGreetingWorkflowInput {
        String name
        String apiKey
    }
}
