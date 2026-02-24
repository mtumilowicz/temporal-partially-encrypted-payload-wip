package org.example.temporal;

import io.quarkiverse.temporal.TemporalWorkflow;

@TemporalWorkflow(workers = "<default>")
public class GreetingWorkflowImpl implements GreetingWorkflow {

    @Override
    public String composeGreeting(String name, int repeatCount) {
        String normalizedName = (name == null || name.isBlank()) ? "world" : name.trim();
        int safeRepeatCount = Math.max(1, Math.min(repeatCount, 5));

        StringBuilder response = new StringBuilder();
        for (int i = 1; i <= safeRepeatCount; i++) {
            if (i > 1) {
                response.append(" | ");
            }
            response.append("Hello ").append(normalizedName).append(" #").append(i);
        }
        return response.toString();
    }
}
