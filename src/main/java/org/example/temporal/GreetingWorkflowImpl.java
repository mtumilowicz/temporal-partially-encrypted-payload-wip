package org.example.temporal;

import io.quarkiverse.temporal.TemporalWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

@TemporalWorkflow(workers = "<default>")
public class GreetingWorkflowImpl implements GreetingWorkflow {

    private final GreetingActivity greetingActivity = Workflow.newActivityStub(
            GreetingActivity.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(5)).build()
    );

    private final ApiKeyActivity apiKeyActivity = Workflow.newActivityStub(
            ApiKeyActivity.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(5)).build()
    );

    @Override
    public GreetingWorkflowOutput composeGreeting(GreetingWorkflowInput input) {
        String output = greetingActivity.buildGreeting(input.name(), input.repeatCount());
        ApiKeyProcessingResult apiKeyResult = apiKeyActivity.processApiKey(
                input.apiKey(),
                input.includeSensitiveOutput()
        );

        return new GreetingWorkflowOutput(
                output,
                apiKeyResult.apiKeyFingerprint(),
                apiKeyResult.sensitiveOutputPart()
        );
    }
}
