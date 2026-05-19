package org.example

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue

@QuarkusTest
class TemporalWorkflowResourceIT {

    @Test
    void 'returns example workflow response'() {
        Map body = RestAssured.given()
                .contentType('application/json')
                .body('{"name":"Temporal","apiKey":"sk_test_1_1234567890","parameters":{"secretApiKey":"sk_test_2_1234567890"}}')
                .when()
                .post('/temporal/example')
                .then()
                .statusCode(200)
                .extract()
                .as(Map)

        assertEquals('Temporal', body.oldName)
        assertEquals('new_name_hardcoded', body.newName)
        assertEquals('sk_test_1_1234567890', body.oldApiKey)
        assertEquals('sk_test_1_1234567890_rotated', body.newApiKey)
        assertNotNull(body.date)
        assertEquals('sk_test_2_1234567890', body.oldApiKey2)
        assertEquals('sk_test_2_1234567890_rotated', body.newApiKey2)
        assertNotNull(body.date2)
        assertTrue((body.encryptedApiKeyFromParameters as String).startsWith('enc:v1:'))
    }
}
