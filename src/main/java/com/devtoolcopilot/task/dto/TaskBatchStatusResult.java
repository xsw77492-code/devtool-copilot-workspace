package com.devtoolcopilot.task.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TaskBatchStatusResult {
    private Integer ok;
    private List<TaskBatchStatusFailure> failed;

    public static TaskBatchStatusResult empty() {
        TaskBatchStatusResult r = new TaskBatchStatusResult();
        r.setOk(0);
        r.setFailed(new ArrayList<>());
        return r;
    }
}

