package org.example;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.example.temporal.UtcTimestampProvider;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Mockito.when;

@QuarkusTest
class ExampleResourceTest {
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

}
