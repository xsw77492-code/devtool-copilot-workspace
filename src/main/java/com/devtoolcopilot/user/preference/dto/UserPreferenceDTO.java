package com.devtoolcopilot.user.preference.dto;

import lombok.Data;

@Data
public class UserPreferenceDTO {
    private String accentKey;
    private String timezone;
    private Integer weekStart;
    private Integer reduceMotion;
}

