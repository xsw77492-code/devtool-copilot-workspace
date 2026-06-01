package com.devtoolcopilot.task.deliverable.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskDeliverableItem {
    private Long id;
    private Long projectId;
    private Long taskId;
    private Long userId;
    private String username;
    private String type;
    private String title;
    private String url;
    private String content;
    private String status;
    private Long sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

