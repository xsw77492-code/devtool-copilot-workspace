package com.devtoolcopilot.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class NotificationSettingsResponse {
    private Integer dndEnabled;
    private Integer dndStartMinute;
    private Integer dndEndMinute;
    private List<NotificationTypePrefItem> typePrefs;
}

