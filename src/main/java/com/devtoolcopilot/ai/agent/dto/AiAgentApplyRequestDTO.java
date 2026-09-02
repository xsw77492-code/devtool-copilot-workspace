package com.devtoolcopilot.ai.agent.dto;

import lombok.Data;

@Data
public class AiAgentApplyRequestDTO {
    private Long projectId;
    private AiAgentPlanResponseDTO plan;
    private Long milestoneId;
    private Long parentTaskId;
}
