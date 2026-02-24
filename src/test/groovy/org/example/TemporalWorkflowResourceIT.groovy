package org.example

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull

@QuarkusTest
class TemporalWorkflowResourceIT {

    @Test
    void 'returns example workflow response'() {
        Map body = RestAssured.given()
                .contentType('application/json')
                .body('{"name":"Temporal","apiKey":"sk_test_1234567890"}')
                .when()
                .post('/temporal/example')
                .then()
                .statusCode(200)
                .extract()
                .as(Map)

        assertEquals('Temporal', body.oldName)
        assertEquals('new_name_hardcoded', body.newName)
        assertEquals('sk_test_1234567890', body.oldApiKey)
        assertEquals('sk_new_hardcoded_123', body.newApiKey)
        assertNotNull(body.date)
    }
}
