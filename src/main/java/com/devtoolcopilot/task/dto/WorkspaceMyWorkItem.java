package com.devtoolcopilot.task.dto;

import com.devtoolcopilot.task.entity.TaskStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkspaceMyWorkItem {
    private Long taskId;
    private Long projectId;
    private String projectName;
    private String title;
    private TaskStatus status;
    private String priority;
    private LocalDateTime dueTime;
    private LocalDateTime updatedAt;
}

