package org.example.temporal;

import io.quarkiverse.temporal.TemporalActivity;

public class NameActivityImpl implements NameActivity {

    @Override
    public String generateNewName(String oldName) {
        return "new_name_hardcoded";
    }
}
