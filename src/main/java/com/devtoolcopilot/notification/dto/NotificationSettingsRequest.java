package com.devtoolcopilot.notification.dto;

import lombok.Data;

import java.util.List;

@Data
public class NotificationSettingsRequest {
    private Integer dndEnabled;
    private Integer dndStartMinute;
    private Integer dndEndMinute;
    private List<NotificationTypePrefItem> typePrefs;
}

