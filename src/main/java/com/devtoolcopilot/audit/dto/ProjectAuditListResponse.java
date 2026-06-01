package com.devtoolcopilot.audit.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProjectAuditListResponse {
    private Long nextCursor;
    private List<ProjectAuditItem> list;
}

