package com.devtoolcopilot.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProjectSummaryResponse {
    private Long projectId;
    private String report;
}
