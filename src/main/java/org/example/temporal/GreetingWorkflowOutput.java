package org.example.temporal;

public record GreetingWorkflowOutput(
        String newName,
        RotateResult rotateResult
) {
}
