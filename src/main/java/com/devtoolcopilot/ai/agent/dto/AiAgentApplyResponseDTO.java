package com.devtoolcopilot.ai.agent.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiAgentApplyResponseDTO {
    private Long projectId;
    private List<Long> taskIds;
    private Integer requestedCount;
    private Integer createdCount;
    private List<String> failedTitles;
}
