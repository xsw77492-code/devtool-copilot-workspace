package com.devtoolcopilot.ai.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum TaskPriority {
    HIGH,
    MEDIUM,
    LOW;

    @JsonCreator
    public static TaskPriority from(String value) {
        if (value == null) {
            return null;
        }
        try {
            return TaskPriority.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            return MEDIUM;
        }
    }
}
