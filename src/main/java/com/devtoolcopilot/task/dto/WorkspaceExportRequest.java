package com.devtoolcopilot.task.dto;

import lombok.Data;

@Data
public class WorkspaceExportRequest {
    private Long projectId;
    private String mode;
    private String q;
    private Boolean overdueOnly;
    private String sort;
}

