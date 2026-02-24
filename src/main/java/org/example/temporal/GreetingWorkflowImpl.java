package org.example.temporal;

import io.quarkiverse.temporal.TemporalWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

@TemporalWorkflow(workers = "<default>")
public class GreetingWorkflowImpl implements GreetingWorkflow {

    private final ApiKeyActivity apiKeyActivity = Workflow.newActivityStub(
            ApiKeyActivity.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(5)).build()
    );

    @Override
    public GreetingWorkflowOutput composeGreeting(GreetingWorkflowInput input) {
        ApiKeyProcessingResult apiKeyResult = apiKeyActivity.rotateApiKey(input.apiKey());

        return new GreetingWorkflowOutput(
                apiKeyResult.oldApiKey(),
                apiKeyResult.newApiKey()
        );
    }
}
