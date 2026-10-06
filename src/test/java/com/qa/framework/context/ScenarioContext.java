package com.qa.framework.context;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple key/value store to share data between step definition classes
 * within the same scenario (e.g. the Excel row currently in use).
 */
public class ScenarioContext {

    private final Map<String, Object> data = new HashMap<>();

    public void set(String key, Object value) {
        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        if (!data.containsKey(key)) {
            throw new IllegalStateException("No value stored in scenario context for key: " + key);
        }
        return (T) data.get(key);
    }

    public boolean contains(String key) {
        return data.containsKey(key);
    }
}
