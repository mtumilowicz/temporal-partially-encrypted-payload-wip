package org.example.temporal;

import io.quarkiverse.temporal.TemporalActivity;

@TemporalActivity(workers = "<default>")
public class GreetingActivityImpl implements GreetingActivity {

    @Override
    public String buildGreeting(String name, int repeatCount) {
        StringBuilder response = new StringBuilder();
        for (int i = 1; i <= repeatCount; i++) {
            if (i > 1) {
                response.append(" | ");
            }
            response.append("Hello ").append(name).append(" #").append(i);
        }
        return response.toString();
    }
}
