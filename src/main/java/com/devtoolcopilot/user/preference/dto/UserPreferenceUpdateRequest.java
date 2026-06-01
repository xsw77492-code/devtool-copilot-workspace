package com.devtoolcopilot.user.preference.dto;

import lombok.Data;

@Data
public class UserPreferenceUpdateRequest {
    private String accentKey;
    private String timezone;
    private Integer weekStart;
    private Integer reduceMotion;
}

