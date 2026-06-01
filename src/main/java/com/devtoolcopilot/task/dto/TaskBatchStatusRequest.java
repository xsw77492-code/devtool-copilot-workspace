package com.devtoolcopilot.task.dto;

import com.devtoolcopilot.task.entity.TaskStatus;
import lombok.Data;

import java.util.List;

@Data
public class TaskBatchStatusRequest {
    private List<Long> taskIds;
    private TaskStatus status;
    private Boolean forceDone;
}
