package org.example.temporal;

public record ExampleWorkflowOutput(
        String newName,
        RotateResult rotateResult1,
        RotateResult rotateResult2,
        String encryptedApiKeyFromParameters
) {
}
