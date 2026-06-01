package com.devtoolcopilot.task.template.dto;

import lombok.Data;

@Data
public class TaskTemplateUpsertRequest {
    private Long projectId;
    private String name;
    private String payloadJson;
}

