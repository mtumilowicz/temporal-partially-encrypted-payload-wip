package org.example.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface NameActivity {

    @ActivityMethod
    String generateNewName(String oldName);
}
