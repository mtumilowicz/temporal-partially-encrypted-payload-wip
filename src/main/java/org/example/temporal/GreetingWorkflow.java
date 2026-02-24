package org.example.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface GreetingWorkflow {

    @WorkflowMethod
    GreetingWorkflowOutput composeGreeting(GreetingWorkflowInput input);
}
