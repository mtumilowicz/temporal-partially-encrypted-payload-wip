package org.example;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.example.temporal.GreetingWorkflow;
import org.example.temporal.GreetingWorkflowInput;
import org.example.temporal.GreetingWorkflowOutput;
import org.example.temporal.codec.SecureString;

import java.util.UUID;

@Path("/temporal")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TemporalWorkflowResource {

    @Inject
    WorkflowClient workflowClient;

    @POST
    @Path("/greeting")
    public GreetingWorkflowResponse startGreetingWorkflow(GreetingWorkflowRequest request) {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue("<default>")
                .setWorkflowId("greeting-" + UUID.randomUUID())
                .build();

        GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class, options);
        GreetingWorkflowInput workflowInput = new GreetingWorkflowInput(
                request.name(),
                new SecureString(request.apiKey())
        );
        WorkflowClient.start(workflow::composeGreeting, workflowInput);
        GreetingWorkflowOutput output = WorkflowStub.fromTyped(workflow).getResult(GreetingWorkflowOutput.class);

        return new GreetingWorkflowResponse(
                request.name(),
                output.newName(),
                output.rotateResult().oldApiKey() == null ? null : output.rotateResult().oldApiKey().value(),
                output.rotateResult().newApiKey() == null ? null : output.rotateResult().newApiKey().value(),
                output.rotateResult().date()
        );
    }

    public record GreetingWorkflowRequest(
            String name,
            String apiKey
    ) {
    }

    public record GreetingWorkflowResponse(
            String oldName,
            String newName,
            String oldApiKey,
            String newApiKey,
            String date
    ) {
    }
}
