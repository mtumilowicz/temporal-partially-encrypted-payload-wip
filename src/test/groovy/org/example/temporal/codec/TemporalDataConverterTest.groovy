package org.example.temporal.codec

import com.fasterxml.jackson.databind.ObjectMapper
import io.temporal.api.common.v1.Payload
import io.temporal.api.common.v1.Payloads
import io.temporal.common.converter.DataConverter
import io.temporal.common.converter.DataConverterException
import org.example.security.AllowUnsafeChars
import org.example.security.PartialPayloadCrypto
import org.example.temporal.ExampleWorkflowInput
import spock.lang.Specification

import java.util.Arrays
import java.util.Optional
import java.util.UUID

class TemporalDataConverterTest extends Specification {

    private static final String ENCRYPTED_PREFIX = 'enc:v1:'
    private static final String TEST_KEY_BASE64 = 'MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY='
    private static final UUID WORKFLOW_ID = UUID.fromString('123e4567-e89b-12d3-a456-426614174000')

    private final ObjectMapper objectMapper = new ObjectMapper()
    private final PartialPayloadCrypto crypto = new PartialPayloadCrypto(TEST_KEY_BASE64)
    private final DataConverter dataConverter = new TemporalDataConverterProducer()
            .temporalDataConverter(crypto)

    def 'auto-encrypts and decrypts SecureString via data converter'() {
        given:
        String plainSecret = 'sk_test_roundtrip_secret'
        String plainName = 'Temporal'
        ExampleWorkflowInput input = new ExampleWorkflowInput(
                plainName,
                new SecureString(plainSecret.toCharArray(), WORKFLOW_ID)
        )

        when:
        Payloads payloads = dataConverter.toPayloads(input).orElseThrow()
        SerializedExampleWorkflowInput payload = payloadAsInput(payloads)

        then:
        payload.name == plainName
        payload.apiKey.value.startsWith(ENCRYPTED_PREFIX)
        !payload.apiKey.value.contains(plainSecret)
        payload.apiKey.workflowId == WORKFLOW_ID.toString()

        when:
        ExampleWorkflowInput decoded = dataConverter.fromPayloads(
                0,
                Optional.of(payloads),
                ExampleWorkflowInput.class,
                ExampleWorkflowInput.class
        )

        then:
        decoded.apiKey().workflowId() == WORKFLOW_ID
        @AllowUnsafeChars('test verification that converter round-trip restores secret')
        char[] chars = decoded.apiKey().unsafeChars()
        try {
            Arrays.equals(plainSecret.toCharArray(), chars)
        } finally {
            Arrays.fill(chars, (char) 0)
        }
    }

    def 'fails deserialization when secure payload token is not encrypted'() {
        given:
        String json = '{"name":"VisibleName","apiKey":"not-encrypted"}'
        Payloads payloads = Payloads.newBuilder()
                .addPayloads(Payload.newBuilder()
                        .putMetadata('encoding', com.google.protobuf.ByteString.copyFromUtf8('json/plain'))
                        .setData(com.google.protobuf.ByteString.copyFromUtf8(json))
                        .build())
                .build()

        when:
        dataConverter.fromPayloads(
                0,
                Optional.of(payloads),
                ExampleWorkflowInput.class,
                ExampleWorkflowInput.class
        )

        then:
        thrown(DataConverterException)
    }

    private SerializedExampleWorkflowInput payloadAsInput(Payloads payloads) {
        String json = payloads.getPayloads(0).getData().toStringUtf8()
        return objectMapper.readValue(json, SerializedExampleWorkflowInput.class)
    }

    private static class SerializedExampleWorkflowInput {
        String name
        SerializedSecureString apiKey
    }

    private static class SerializedSecureString {
        String value
        String workflowId
    }
}
