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
import org.example.security.AllowUnsafeChars;
import org.example.temporal.GreetingWorkflow;
import org.example.temporal.GreetingWorkflowInput;
import org.example.temporal.GreetingWorkflowOutput;
import org.example.temporal.codec.SecureString;

import java.util.Arrays;
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
                new SecureString(request.apiKey().toCharArray())
        );
        WorkflowClient.start(workflow::composeGreeting, workflowInput);
        GreetingWorkflowOutput output = WorkflowStub.fromTyped(workflow).getResult(GreetingWorkflowOutput.class);

        return new GreetingWorkflowResponse(
                request.name(),
                output.newName(),
                toPlainString(output.rotateResult().oldApiKey()),
                toPlainString(output.rotateResult().newApiKey()),
                output.rotateResult().date()
        );
    }

    private static String toPlainString(SecureString secureString) {
        @AllowUnsafeChars("building plaintext secret for endpoint response contract")
        char[] chars = secureString.unsafeChars();
        try {
            return new String(chars);
        } finally {
            Arrays.fill(chars, '\0');
        }
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
