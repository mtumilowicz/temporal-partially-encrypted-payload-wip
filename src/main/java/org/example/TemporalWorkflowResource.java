package org.example;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.example.security.AllowUnsafeChars;
import org.example.temporal.ExampleWorkflow;
import org.example.temporal.ExampleWorkflowInput;
import org.example.temporal.ExampleWorkflowOutput;
import org.example.temporal.codec.SecureString;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Path("/temporal")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TemporalWorkflowResource {

    @Inject
    WorkflowClient workflowClient;

    @POST
    @Path("/example")
    public ExampleWorkflowResponse startExampleWorkflow(ExampleWorkflowRequest request) {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue("<default>")
                .setWorkflowId("example-" + UUID.randomUUID())
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_FAIL)
                .build();

        ExampleWorkflow workflow = workflowClient.newWorkflowStub(ExampleWorkflow.class, options);
        ExampleWorkflowInput workflowInput = new ExampleWorkflowInput(
                request.name(),
                new SecureString(request.apiKey().toCharArray()),
                request.parameters()
        );
        WorkflowClient.start(workflow::run, workflowInput);
        ExampleWorkflowOutput output = WorkflowStub.fromTyped(workflow).getResult(ExampleWorkflowOutput.class);

        return new ExampleWorkflowResponse(
                request.name(),
                output.newName(),
                toPlainString(output.rotateResult1().oldApiKey()),
                toPlainString(output.rotateResult1().newApiKey()),
                output.rotateResult1().date(),
                toPlainString(output.rotateResult2().oldApiKey()),
                toPlainString(output.rotateResult2().newApiKey()),
                output.rotateResult2().date()
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
            String apiKey,
            @JsonDeserialize(using = SecretParametersDeserializer.class)
            Map<String, Object> parameters
    ) {
    }

    public record ExampleWorkflowResponse(
            String oldName,
            String newName,
            String oldApiKey,
            String newApiKey,
            String date,
            String oldApiKey2,
            String newApiKey2,
            String date2
    ) {
    }

    static final class SecretParametersDeserializer extends JsonDeserializer<Map<String, Object>> {
        @Override
        public Map<String, Object> deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            ObjectCodec codec = parser.getCodec();
            JsonNode root = codec.readTree(parser);
            if (!root.isObject()) {
                return context.reportInputMismatch(Map.class, "parameters must be a JSON object");
            }

            Map<String, Object> parameters = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> property : root.properties()) {
                String key = property.getKey();
                JsonNode value = property.getValue();

                if (key.startsWith("secret") && value.isTextual()) {
                    parameters.put(key, new SecureString(value.textValue().toCharArray()));
                } else {
                    parameters.put(key, codec.treeToValue(value, Object.class));
                }
            }
            return parameters;
        }
    }
}
