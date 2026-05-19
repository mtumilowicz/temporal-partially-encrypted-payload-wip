package org.example.temporal;

import io.quarkiverse.temporal.TemporalWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.example.temporal.codec.SecureString;

import java.time.Duration;

public class ExampleWorkflowImpl implements ExampleWorkflow {

    private static final RetryOptions ONE_RETRY = RetryOptions.newBuilder()
            .setMaximumAttempts(2)
            .build();

    private final NameActivity nameActivity = Workflow.newActivityStub(
            NameActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(5))
                    .setRetryOptions(ONE_RETRY)
                    .build()
    );

    private final ApiKeyActivity apiKeyActivity = Workflow.newActivityStub(
            ApiKeyActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(5))
                    .setRetryOptions(ONE_RETRY)
                    .build()
    );

    @Override
    public ExampleWorkflowOutput run(ExampleWorkflowInput input) {
        String newName = nameActivity.generateNewName(input.name());
        RotateResult rotateResult1 = apiKeyActivity.rotateApiKey(input.apiKey());
        RotateResult rotateResult2 = apiKeyActivity.rotateApiKey((SecureString) input.parameters().get("secretApiKey"));
        String encryptedApiKeyFromParameters = apiKeyActivity.encryptedApiKeyFromParameters(input.parameters());

        return new ExampleWorkflowOutput(
                newName,
                rotateResult1,
                rotateResult2,
                encryptedApiKeyFromParameters
        );
    }
}
