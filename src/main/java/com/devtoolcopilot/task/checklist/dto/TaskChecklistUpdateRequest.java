package com.devtoolcopilot.task.checklist.dto;

import lombok.Data;

@Data
public class TaskChecklistUpdateRequest {
    private String content;
    private Boolean done;
}

