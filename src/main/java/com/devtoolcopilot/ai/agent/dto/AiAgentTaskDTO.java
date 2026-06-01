package com.devtoolcopilot.ai.agent.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiAgentTaskDTO {
    private String title;
    private String description;
    private String priority;
    private List<String> checklist;
    private List<AiAgentDeliverableDTO> deliverables;
    private List<String> sources;
}

