package org.example

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured
import spock.lang.Specification

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.matchesRegex

@QuarkusIntegrationTest
class TemporalWorkflowResourceIT extends Specification {

    def 'returns greeting workflow response'() {
        expect:
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
                .body('date', matchesRegex('^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$'))
    }
}
