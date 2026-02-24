package org.example;

import io.temporal.common.converter.DataConverter;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.example.security.AllowUnsafeChars;
import org.example.temporal.UtcTimestampProvider;
import org.example.temporal.GreetingWorkflowInput;
import org.example.temporal.codec.SecureString;

import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@QuarkusTest
class ExampleResourceTest {
    @Inject
    DataConverter dataConverter;

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

        Optional<io.temporal.api.common.v1.Payloads> payloadsOpt = dataConverter.toPayloads(input);
        assertTrue(payloadsOpt.isPresent());

        String serialized = payloadsOpt.get().getPayloads(0).getData().toStringUtf8();
        assertTrue(serialized.contains("\"name\":\"" + plainName + "\""));
        assertFalse(serialized.contains(plainSecret));
        assertTrue(serialized.contains("enc:v1:"));

        GreetingWorkflowInput decoded = dataConverter.fromPayloads(
                0,
                payloadsOpt,
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

        Optional<io.temporal.api.common.v1.Payloads> payloadsOpt = dataConverter.toPayloads(input);
        assertTrue(payloadsOpt.isPresent());

        String serialized = payloadsOpt.get().getPayloads(0).getData().toStringUtf8();
        assertTrue(serialized.contains("\"name\":\"" + plainName + "\""));
        assertFalse(serialized.contains("\"name\":\"enc:v1:"));
        assertFalse(serialized.contains(plainSecret));
        assertTrue(serialized.contains("enc:v1:"));
    }

}
