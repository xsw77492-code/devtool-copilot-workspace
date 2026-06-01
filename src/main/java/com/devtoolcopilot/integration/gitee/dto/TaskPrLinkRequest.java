package com.devtoolcopilot.integration.gitee.dto;

import lombok.Data;

@Data
public class TaskPrLinkRequest {
    private Long projectId;
    private Long taskId;
    private String pr;
}

