package org.example.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface GreetingActivity {

    @ActivityMethod
    String buildGreeting(String name, int repeatCount);
}
