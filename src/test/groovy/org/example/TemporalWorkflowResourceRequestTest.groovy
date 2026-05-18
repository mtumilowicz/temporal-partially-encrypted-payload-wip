package org.example

import com.fasterxml.jackson.databind.ObjectMapper
import org.example.security.AllowUnsafeChars
import org.example.temporal.codec.SecureString
import org.junit.jupiter.api.Test

import java.util.Arrays

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class TemporalWorkflowResourceRequestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()

    @Test
    void 'deserializes textual secret parameters as secure strings'() {
        String json = '''
                {
                    "name": "Temporal",
                    "apiKey": "sk_test_1234567890",
                    "parameters": {
                        "secretToken": "param_secret",
                        "visible": "plain"
                    }
                }
                '''

        TemporalWorkflowResource.ExampleWorkflowRequest request = readRequest(json)

        assertTrue(request.parameters().secretToken instanceof SecureString)
        assertTrue(secureStringEquals(request.parameters().secretToken as SecureString, 'param_secret'))
        assertEquals('plain', request.parameters().visible)
    }

    @Test
    void 'leaves non-secret textual parameters unchanged'() {
        String json = '''
                {
                    "name": "Temporal",
                    "apiKey": "sk_test_1234567890",
                    "parameters": {
                        "token": "param_secret",
                        "nested": {
                            "secretToken": "nested_secret"
                        }
                    }
                }
                '''

        TemporalWorkflowResource.ExampleWorkflowRequest request = readRequest(json)

        assertEquals('param_secret', request.parameters().token)
        assertTrue(request.parameters().nested instanceof Map)
        assertEquals('nested_secret', (request.parameters().nested as Map).secretToken)
        assertFalse((request.parameters().nested as Map).secretToken instanceof SecureString)
    }

    @Test
    void 'rejects non-textual secret parameters'() {
        String json = '''
                {
                    "name": "Temporal",
                    "apiKey": "sk_test_1234567890",
                    "parameters": {
                        "secretEnabled": true,
                        "secretRetries": 3,
                        "secretItems": ["a", "b"]
                    }
                }
                '''

        Exception e = assertThrows(Exception.class, {
            readRequest(json)
        })

        assertTrue(e.message.contains('secret parameters must be strings'))
    }

    @Test
    void 'allows null parameters'() {
        String json = '''
                {
                    "name": "Temporal",
                    "apiKey": "sk_test_1234567890",
                    "parameters": null
                }
                '''

        TemporalWorkflowResource.ExampleWorkflowRequest request = readRequest(json)

        assertNull(request.parameters())
    }

    @Test
    void 'rejects non-object parameters'() {
        String json = '''
                {
                    "name": "Temporal",
                    "apiKey": "sk_test_1234567890",
                    "parameters": ["not", "an", "object"]
                }
                '''

        Exception e = assertThrows(Exception.class, {
            readRequest(json)
        })

        assertTrue(e.message.contains('parameters must be a JSON object'))
    }

    private static TemporalWorkflowResource.ExampleWorkflowRequest readRequest(String json) {
        return MAPPER.readValue(json, TemporalWorkflowResource.ExampleWorkflowRequest.class)
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
