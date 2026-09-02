package com.devtoolcopilot.task.dto;

import lombok.Data;

@Data
public class TaskBatchStatusFailure {
    private Long taskId;
    private Integer code;
    private String message;

    public static TaskBatchStatusFailure of(Long taskId, Integer code, String message) {
        TaskBatchStatusFailure f = new TaskBatchStatusFailure();
        f.setTaskId(taskId);
        f.setCode(code);
        f.setMessage(message);
        return f;
    }
}

