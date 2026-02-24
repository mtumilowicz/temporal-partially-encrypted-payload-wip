package org.example.temporal.codec

import io.temporal.common.converter.DataConverter
import io.temporal.common.converter.DataConverterException
import org.example.security.PartialPayloadCrypto
import org.example.temporal.ExampleWorkflowInput
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class TemporalDataConverterNoContextTest {

    private static final String TEST_KEY_BASE64 = 'MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY='

    @Test
    void 'fails encryption when workflow context is missing'() {
        PartialPayloadCrypto crypto = new PartialPayloadCrypto(TEST_KEY_BASE64)
        DataConverter dataConverter = new TemporalDataConverterProducer()
                .temporalDataConverter(crypto)
        ExampleWorkflowInput input = new ExampleWorkflowInput(
                'NoContext',
                new SecureString('sk_test_no_context'.toCharArray()),
                [:]
        )

        DataConverterException e = assertThrows(DataConverterException.class, {
            dataConverter.toPayloads(input)
        })

        assertTrue(e.toString().contains('workflowId'))
    }
}
