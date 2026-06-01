package com.devtoolcopilot.ai.dto;

import lombok.Data;

@Data
public class AiChatHistoryDTO {
    private Long id;
    private Long projectId;
    private String prompt;
    private String response;
    private String createTime;
}
