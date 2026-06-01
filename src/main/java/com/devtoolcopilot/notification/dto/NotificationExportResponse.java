package com.devtoolcopilot.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NotificationExportResponse {
    private String filename;
    private String content;
}

