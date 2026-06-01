package com.devtoolcopilot.notification.dto;

import lombok.Data;

import java.util.List;

@Data
public class NotificationReadBatchRequest {
    private List<Long> ids;
}

