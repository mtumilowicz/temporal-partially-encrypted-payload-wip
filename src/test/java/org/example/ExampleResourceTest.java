package org.example;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class ExampleResourceTest {
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
        given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "Temporal",
                          "repeatCount": 2
                        }
                        """)
                .when().post("/temporal/greeting")
                .then()
                .statusCode(200)
                .body("workflowId", containsString("greeting-"))
                .body("runId", notNullValue())
                .body("name", is("Temporal"))
                .body("repeatCount", is(2))
                .body("output", containsString("Hello Temporal #1"));
    }

}
