package org.example;

import io.temporal.api.common.v1.WorkflowExecution;
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
        String name = request != null && request.name() != null ? request.name().trim() : "";
        if (name.isEmpty()) {
            name = "world";
        }

        int repeatCount = request != null ? request.repeatCount() : 1;
        repeatCount = Math.max(1, Math.min(repeatCount, 5));

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue("<default>")
                .setWorkflowId("greeting-" + UUID.randomUUID())
                .build();

        GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class, options);
        WorkflowExecution execution = WorkflowClient.start(workflow::composeGreeting, name, repeatCount);
        String output = WorkflowStub.fromTyped(workflow).getResult(String.class);

        return new GreetingWorkflowResponse(execution.getWorkflowId(), execution.getRunId(), name, repeatCount, output);
    }

    public record GreetingWorkflowRequest(String name, int repeatCount) {
    }

    public record GreetingWorkflowResponse(String workflowId, String runId, String name, int repeatCount, String output) {
    }
}
