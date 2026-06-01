package com.devtoolcopilot.ai.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum AiRiskLevel {
    LOW,
    MEDIUM,
    HIGH;

    @JsonCreator
    public static AiRiskLevel from(String value) {
        if (value == null) {
            return MEDIUM;
        }
        try {
            return AiRiskLevel.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            return MEDIUM;
        }
    }
}
