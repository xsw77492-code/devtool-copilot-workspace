package com.devtoolcopilot.ai.agent.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiAgentPlanResponseDTO {
    private String goal;
    private List<AiAgentTaskDTO> tasks;
    private List<AiAgentSourceDTO> sources;
}

