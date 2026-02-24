package org.example

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import spock.lang.Specification

@QuarkusTest
class TemporalWorkflowResourceIT extends Specification {

    def 'returns example workflow response'() {
        when:
        def body = RestAssured.given()
                .contentType('application/json')
                .body('{"name":"Temporal","apiKey":"sk_test_1234567890"}')
                .when()
                .post('/temporal/example')
                .then()
                .statusCode(200)
                .extract()
                .as(Map)

        then:
        with(body) {
            oldName == 'Temporal'
            newName == 'new_name_hardcoded'
            oldApiKey == 'sk_test_1234567890'
            newApiKey == 'sk_new_hardcoded_123'
            date
        }
    }
}
