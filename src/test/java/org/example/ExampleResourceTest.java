package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.temporal.common.converter.DataConverter;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.example.security.AllowUnsafeChars;
import org.example.temporal.UtcTimestampProvider;
import org.example.temporal.GreetingWorkflowInput;
import org.example.temporal.codec.SecureString;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.Arrays;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@QuarkusTest
class ExampleResourceTest {
    private static final String ENCRYPTED_PREFIX = "enc:v1:";

    @Inject
    DataConverter dataConverter;

    @Inject
    ObjectMapper objectMapper;

    @InjectMock
    UtcTimestampProvider utcTimestampProvider;

    @Test
    void testHelloEndpoint() {
        given()
                .when().get("/hello")
                .then()
                .statusCode(200)
                .body(is("Hello from Quarkus REST"));
    }

    @Test
    void testTemporalGreetingWorkflowEndpoint() {
        String expectedDate = "2026-02-24T17:15:30.123Z";
        when(utcTimestampProvider.nowIsoMillis()).thenReturn(expectedDate);

        given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "Temporal",
                          "apiKey": "sk_test_1234567890"
                        }
                        """)
                .when().post("/temporal/greeting")
                .then()
                .statusCode(200)
                .body("oldName", is("Temporal"))
                .body("newName", is("new_name_hardcoded"))
                .body("oldApiKey", is("sk_test_1234567890"))
                .body("newApiKey", is("sk_new_hardcoded_123"))
                .body("date", is(expectedDate));
    }

    @Test
    void testSecureStringIsSerializedEncryptedByTemporalDataConverter() {
        String plainSecret = "sk_test_roundtrip_secret";
        String plainName = "Temporal";
        GreetingWorkflowInput input = new GreetingWorkflowInput(
                plainName,
                new SecureString(plainSecret.toCharArray())
        );

        io.temporal.api.common.v1.Payloads payloads = dataConverter.toPayloads(input).orElseThrow();

        JsonNode payloadJson = payloadAsJson(payloads);
        assertEquals(plainName, payloadJson.get("name").asText());
        String encryptedApiKey = payloadJson.get("apiKey").asText();
        assertTrue(encryptedApiKey.startsWith(ENCRYPTED_PREFIX));
        assertFalse(encryptedApiKey.contains(plainSecret));

        GreetingWorkflowInput decoded = dataConverter.fromPayloads(
                0,
                java.util.Optional.of(payloads),
                GreetingWorkflowInput.class,
                GreetingWorkflowInput.class
        );

        @AllowUnsafeChars("test verification that converter round-trip restores secret")
        char[] chars = decoded.apiKey().unsafeChars();
        try {
            assertTrue(Arrays.equals(plainSecret.toCharArray(), chars));
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    @Test
    void testNonSecureFieldIsNotEncrypted() {
        String plainSecret = "sk_test_nonsecure_check";
        String plainName = "VisibleName";
        GreetingWorkflowInput input = new GreetingWorkflowInput(
                plainName,
                new SecureString(plainSecret.toCharArray())
        );

        io.temporal.api.common.v1.Payloads payloads = dataConverter.toPayloads(input).orElseThrow();

        JsonNode payloadJson = payloadAsJson(payloads);
        assertEquals(plainName, payloadJson.get("name").asText());
        String encryptedApiKey = payloadJson.get("apiKey").asText();
        assertNotNull(encryptedApiKey);
        assertTrue(encryptedApiKey.startsWith(ENCRYPTED_PREFIX));
        assertFalse(encryptedApiKey.contains(plainSecret));
    }

    private JsonNode payloadAsJson(io.temporal.api.common.v1.Payloads payloads) {
        String json = payloads.getPayloads(0).getData().toStringUtf8();
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            throw new AssertionError("Payload is not valid JSON: " + json, e);
        }
    }

}
