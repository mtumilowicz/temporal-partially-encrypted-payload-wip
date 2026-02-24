package org.example

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import org.junit.jupiter.api.Test

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.matchesRegex
import static org.hamcrest.Matchers.notNullValue

@QuarkusTest
class TemporalWorkflowResourceIT {

    @Test
    void returnsGreetingWorkflowResponse() {
        RestAssured.given()
                .contentType('application/json')
                .body('{"name":"Temporal","apiKey":"sk_test_1234567890"}')
                .when()
                .post('/temporal/greeting')
                .then()
                .statusCode(200)
                .body('oldName', equalTo('Temporal'))
                .body('newName', equalTo('new_name_hardcoded'))
                .body('oldApiKey', equalTo('sk_test_1234567890'))
                .body('newApiKey', equalTo('sk_new_hardcoded_123'))
                .body('date', notNullValue())
    }
}
