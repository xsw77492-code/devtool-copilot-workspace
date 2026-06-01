package com.devtoolcopilot.task.dto;

import lombok.Data;
import com.devtoolcopilot.task.entity.TaskStatus;

@Data
public class TaskUpdateRequest {
    private TaskStatus status;
    private String baseUpdatedAt;
    private Boolean forceDone;
}
