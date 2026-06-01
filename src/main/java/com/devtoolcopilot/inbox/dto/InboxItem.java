package com.devtoolcopilot.inbox.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class InboxItem {
    private Long id;
    private String category;
    private String title;
    private String content;
    private Long projectId;
    private Long taskId;
    private Long commentId;
    private Long notificationId;
    private Integer isRead;
    private Integer isHandled;
    private LocalDateTime updateTime;
    private LocalDateTime createTime;
}

