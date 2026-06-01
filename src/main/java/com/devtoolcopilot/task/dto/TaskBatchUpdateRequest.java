package com.devtoolcopilot.task.dto;

import lombok.Data;

import java.util.List;

@Data
public class TaskBatchUpdateRequest {
    private List<Long> taskIds;

    private String priority;
    private Long assigneeId;
    private Long dueTime;

    private Boolean clearAssignee;
    private Boolean clearDueTime;
}

