package com.devtoolcopilot.ai.agent.dto;

import lombok.Data;

@Data
public class AiAgentCrewResponseDTO {
    private String pm;
    private String techLead;
    private AiAgentPlanResponseDTO plan;
}

