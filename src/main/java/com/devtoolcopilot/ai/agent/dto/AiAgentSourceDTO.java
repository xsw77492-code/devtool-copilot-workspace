package com.devtoolcopilot.ai.agent.dto;

import lombok.Data;

@Data
public class AiAgentSourceDTO {
    private String sourceId;
    private String type;
    private Long refId;
    private String title;
    private String snippet;
}

