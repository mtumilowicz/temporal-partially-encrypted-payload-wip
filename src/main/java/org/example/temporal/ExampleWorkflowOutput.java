package org.example.temporal;

public record ExampleWorkflowOutput(
        String newName,
        RotateResult rotateResult
) {
}
