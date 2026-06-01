package com.devtoolcopilot.task.checklist.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskChecklistItemDTO {
    private Long id;
    private Long projectId;
    private Long taskId;
    private Long userId;
    private String username;
    private String content;
    private Integer isDone;
    private LocalDateTime doneTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

