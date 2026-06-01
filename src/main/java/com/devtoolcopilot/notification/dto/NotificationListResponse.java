package com.devtoolcopilot.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class NotificationListResponse {
    private Long unreadCount;
    private List<NotificationItem> list;
}

