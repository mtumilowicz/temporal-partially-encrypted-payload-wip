package org.example.temporal.codec

import io.quarkus.test.junit.QuarkusTest
import io.temporal.api.common.v1.Payloads
import io.temporal.common.converter.DataConverter
import io.temporal.payload.context.WorkflowSerializationContext
import jakarta.inject.Inject
import org.example.security.AllowUnsafeChars
import org.example.temporal.ExampleWorkflowInput
import org.junit.jupiter.api.Test

import java.util.Arrays
import java.util.Optional

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

@QuarkusTest
class TemporalDataConverterTest {

    private static final String ENCRYPTED_PREFIX = 'enc:v1:'
    private static final WorkflowSerializationContext CONTEXT =
            new WorkflowSerializationContext('test-namespace', 'wf-test-1')

    @Inject
    DataConverter dataConverter

    @Test
    void 'serializes secure fields as encrypted payload values and deserializes back to original chars'() {
        String plainSecret = 'sk_test_roundtrip_secret'
        String plainName = 'Temporal'
        Map<String, Object> parameters = [attempt: 1, source: 'test']
        ExampleWorkflowInput input = new ExampleWorkflowInput(
                plainName,
                new SecureString(plainSecret.toCharArray()),
                parameters
        )

        DataConverter contextDataConverter = dataConverter.withContext(CONTEXT)
        Payloads payloads = contextDataConverter.toPayloads(input).orElseThrow()
        String rawPayload = payloadAsJson(payloads)

        assertTrue(rawPayload.contains("\"apiKey\":\"${ENCRYPTED_PREFIX}"))
        assertFalse(rawPayload.contains(plainSecret))

        ExampleWorkflowInput decoded = contextDataConverter.fromPayloads(
                0,
                Optional.of(payloads),
                ExampleWorkflowInput.class,
                ExampleWorkflowInput.class
        )

        assertEquals(plainName, decoded.name())
        assertEquals(parameters, decoded.parameters())
        assertTrue(secureStringEquals(decoded.apiKey(), plainSecret))
    }

    @Test
    void 'keeps non-secure fields in plaintext while encrypting secure fields'() {
        String plainSecret = 'sk_test_nonsecure_check'
        String plainName = 'VisibleName'
        Map<String, Object> parameters = [visible: true, count: 2]
        ExampleWorkflowInput input = new ExampleWorkflowInput(
                plainName,
                new SecureString(plainSecret.toCharArray()),
                parameters
        )

        Payloads payloads = dataConverter.withContext(CONTEXT).toPayloads(input).orElseThrow()
        String rawPayload = payloadAsJson(payloads)

        assertTrue(rawPayload.contains("\"name\":\"${plainName}\""))
        assertTrue(rawPayload.contains('"parameters":{"visible":true,"count":2}'))
        assertTrue(rawPayload.contains("\"apiKey\":\"${ENCRYPTED_PREFIX}"))
        assertFalse(rawPayload.contains(plainSecret))
    }

    @Test
    void 'supports root SecureString payload conversion for activity argument style values'() {
        String plainSecret = 'sk_test_root_secret'
        SecureString secure = new SecureString(plainSecret.toCharArray())
        DataConverter contextDataConverter = dataConverter.withContext(CONTEXT)

        Payloads payloads = contextDataConverter.toPayloads(secure).orElseThrow()
        String raw = payloads.getPayloads(0).getData().toStringUtf8()

        assertTrue(raw.startsWith('"enc:v1:'))
        assertFalse(raw.contains(plainSecret))

        SecureString decoded = contextDataConverter.fromPayloads(
                0,
                Optional.of(payloads),
                SecureString.class,
                SecureString.class
        )

        assertTrue(secureStringEquals(decoded, plainSecret))
    }

    private static String payloadAsJson(Payloads payloads) {
        return payloads.getPayloads(0).getData().toStringUtf8()
    }

    private static boolean secureStringEquals(SecureString secureString, String expected) {
        @AllowUnsafeChars('test verification that secure string contains expected chars')
        char[] chars = secureString.unsafeChars()
        try {
            return Arrays.equals(expected.toCharArray(), chars)
        } finally {
            Arrays.fill(chars, (char) 0)
        }
    }
}
