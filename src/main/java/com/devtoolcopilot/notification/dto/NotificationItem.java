package com.devtoolcopilot.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class NotificationItem {
    private Long id;
    private Long projectId;
    private Long taskId;
    private Long commentId;
    private String type;
    private String title;
    private String content;
    private String dataJson;
    private Integer isRead;
    private Integer aggCount;
    private LocalDateTime updateTime;
    private LocalDateTime createTime;
}
