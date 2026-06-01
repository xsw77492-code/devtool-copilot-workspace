package com.devtoolcopilot.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NotificationTypePrefItem {
    private String type;
    private Integer enabled;
}

