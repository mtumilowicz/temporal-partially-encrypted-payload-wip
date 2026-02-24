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
import org.example.security.PartialPayloadCrypto;
import org.example.temporal.ExampleWorkflow;
import org.example.temporal.ExampleWorkflowInput;
import org.example.temporal.ExampleWorkflowOutput;
import org.example.temporal.codec.SecureString;

import java.util.Arrays;
import java.util.UUID;

@Path("/temporal")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TemporalWorkflowResource {

    @Inject
    WorkflowClient workflowClient;

    @Inject
    PartialPayloadCrypto crypto;

    @POST
    @Path("/example")
    public ExampleWorkflowResponse startExampleWorkflow(ExampleWorkflowRequest request) {
        UUID workflowId = UUID.randomUUID();
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue("<default>")
                .setWorkflowId(workflowId.toString())
                .build();

        ExampleWorkflow workflow = workflowClient.newWorkflowStub(ExampleWorkflow.class, options);
        ExampleWorkflowInput workflowInput = new ExampleWorkflowInput(
                request.name(),
                new SecureString(request.apiKey().toCharArray(), workflowId)
        );
        WorkflowClient.start(workflow::run, workflowInput);
        ExampleWorkflowOutput output = WorkflowStub.fromTyped(workflow).getResult(ExampleWorkflowOutput.class);

        return new ExampleWorkflowResponse(
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

    public record ExampleWorkflowRequest(
            String name,
            String apiKey
    ) {
    }

    public record ExampleWorkflowResponse(
            String oldName,
            String newName,
            String oldApiKey,
            String newApiKey,
            String date
    ) {
    }
}
