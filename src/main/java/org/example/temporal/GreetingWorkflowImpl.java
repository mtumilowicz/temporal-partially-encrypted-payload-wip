package org.example.temporal;

import io.quarkiverse.temporal.TemporalWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

@TemporalWorkflow(workers = "<default>")
public class GreetingWorkflowImpl implements GreetingWorkflow {

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
    public GreetingWorkflowOutput composeGreeting(GreetingWorkflowInput input) {
        String newName = nameActivity.generateNewName(input.name());
        RotateResult rotateResult = apiKeyActivity.rotateApiKey(input.apiKey());

        return new GreetingWorkflowOutput(
                newName,
                rotateResult
        );
    }
}
