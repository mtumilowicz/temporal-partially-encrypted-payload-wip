package org.example.temporal.codec

import io.quarkus.test.junit.QuarkusTest
import io.temporal.api.common.v1.Payloads
import io.temporal.common.converter.DataConverter
import io.temporal.payload.context.WorkflowSerializationContext
import jakarta.inject.Inject
import org.example.security.AllowUnsafeChars
import org.example.temporal.ExampleWorkflowInput
import org.example.temporal.ExampleWorkflowOutput
import org.example.temporal.RotateResult
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
    void 'restores textual secret workflow input parameters as secure strings'() {
        String plainApiKey = 'sk_test_input_api_key'
        String plainParameterSecret1 = 'sk_test_parameter_secret_1'
        String plainParameterSecret2 = 'sk_test_parameter_secret_2'
        ExampleWorkflowInput input = new ExampleWorkflowInput(
                'Temporal',
                new SecureString(plainApiKey.toCharArray()),
                [
                        secretApiKey: new SecureString(plainParameterSecret1.toCharArray()),
                        secretToken : new SecureString(plainParameterSecret2.toCharArray()),
                        token       : 'enc:v1:not-a-secret-parameter',
                        visible     : 'plain'
                ]
        )

        DataConverter contextDataConverter = dataConverter.withContext(CONTEXT)
        Payloads payloads = contextDataConverter.toPayloads(input).orElseThrow()
        String rawPayload = payloadAsJson(payloads)

        assertTrue(rawPayload.contains("\"secretApiKey\":\"${ENCRYPTED_PREFIX}"))
        assertTrue(rawPayload.contains("\"secretToken\":\"${ENCRYPTED_PREFIX}"))
        assertFalse(rawPayload.contains(plainApiKey))
        assertFalse(rawPayload.contains(plainParameterSecret1))
        assertFalse(rawPayload.contains(plainParameterSecret2))

        ExampleWorkflowInput decoded = contextDataConverter.fromPayloads(
                0,
                Optional.of(payloads),
                ExampleWorkflowInput.class,
                ExampleWorkflowInput.class
        )

        assertEquals('Temporal', decoded.name())
        assertTrue(secureStringEquals(decoded.apiKey(), plainApiKey))
        assertTrue(decoded.parameters().secretApiKey instanceof SecureString)
        assertTrue(secureStringEquals(decoded.parameters().secretApiKey as SecureString, plainParameterSecret1))
        assertTrue(decoded.parameters().secretToken instanceof SecureString)
        assertTrue(secureStringEquals(decoded.parameters().secretToken as SecureString, plainParameterSecret2))
        assertEquals('enc:v1:not-a-secret-parameter', decoded.parameters().token)
        assertEquals('plain', decoded.parameters().visible)
    }

    @Test
    void 'decrypts encrypted secret workflow input parameters through shared parameter deserializer'() {
        String plainApiKey = 'sk_test_shared_deserializer_api_key'
        String plainParameterSecret = 'sk_test_shared_deserializer_parameter'
        ExampleWorkflowInput input = new ExampleWorkflowInput(
                'Temporal',
                new SecureString(plainApiKey.toCharArray()),
                [
                        secretApiKey: new SecureString(plainParameterSecret.toCharArray())
                ]
        )

        DataConverter contextDataConverter = dataConverter.withContext(CONTEXT)
        Payloads payloads = contextDataConverter.toPayloads(input).orElseThrow()
        String rawPayload = payloadAsJson(payloads)

        assertTrue(rawPayload.contains("\"secretApiKey\":\"${ENCRYPTED_PREFIX}"))
        assertFalse(rawPayload.contains(plainParameterSecret))

        ExampleWorkflowInput decoded = contextDataConverter.fromPayloads(
                0,
                Optional.of(payloads),
                ExampleWorkflowInput.class,
                ExampleWorkflowInput.class
        )

        assertTrue(decoded.parameters().secretApiKey instanceof SecureString)
        assertTrue(secureStringEquals(decoded.parameters().secretApiKey as SecureString, plainParameterSecret))
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

    @Test
    void 'serializes both workflow output rotation results with encrypted secure fields'() {
        String plainOldSecret1 = 'sk_test_output_old_1'
        String plainNewSecret1 = 'sk_test_output_new_1'
        String plainOldSecret2 = 'sk_test_output_old_2'
        String plainNewSecret2 = 'sk_test_output_new_2'
        ExampleWorkflowOutput output = new ExampleWorkflowOutput(
                'new_name_hardcoded',
                new RotateResult(
                        new SecureString(plainOldSecret1.toCharArray()),
                        new SecureString(plainNewSecret1.toCharArray()),
                        '2026-05-18T10:15:30.000Z'
                ),
                new RotateResult(
                        new SecureString(plainOldSecret2.toCharArray()),
                        new SecureString(plainNewSecret2.toCharArray()),
                        '2026-05-18T10:16:30.000Z'
                ),
                'enc:v1:already-encrypted-from-parameters'
        )

        DataConverter contextDataConverter = dataConverter.withContext(CONTEXT)
        Payloads payloads = contextDataConverter.toPayloads(output).orElseThrow()
        String rawPayload = payloadAsJson(payloads)

        assertTrue(rawPayload.contains('"rotateResult1"'))
        assertTrue(rawPayload.contains('"rotateResult2"'))
        assertTrue(rawPayload.contains('"encryptedApiKeyFromParameters":"enc:v1:already-encrypted-from-parameters"'))
        assertTrue(rawPayload.contains("\"oldApiKey\":\"${ENCRYPTED_PREFIX}"))
        assertTrue(rawPayload.contains("\"newApiKey\":\"${ENCRYPTED_PREFIX}"))
        assertFalse(rawPayload.contains(plainOldSecret1))
        assertFalse(rawPayload.contains(plainNewSecret1))
        assertFalse(rawPayload.contains(plainOldSecret2))
        assertFalse(rawPayload.contains(plainNewSecret2))

        ExampleWorkflowOutput decoded = contextDataConverter.fromPayloads(
                0,
                Optional.of(payloads),
                ExampleWorkflowOutput.class,
                ExampleWorkflowOutput.class
        )

        assertEquals(output.newName(), decoded.newName())
        assertTrue(secureStringEquals(decoded.rotateResult1().oldApiKey(), plainOldSecret1))
        assertTrue(secureStringEquals(decoded.rotateResult1().newApiKey(), plainNewSecret1))
        assertEquals(output.rotateResult1().date(), decoded.rotateResult1().date())
        assertTrue(secureStringEquals(decoded.rotateResult2().oldApiKey(), plainOldSecret2))
        assertTrue(secureStringEquals(decoded.rotateResult2().newApiKey(), plainNewSecret2))
        assertEquals(output.rotateResult2().date(), decoded.rotateResult2().date())
        assertEquals(output.encryptedApiKeyFromParameters(), decoded.encryptedApiKeyFromParameters())
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
