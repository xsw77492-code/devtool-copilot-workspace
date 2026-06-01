package com.devtoolcopilot.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiChatRequestDTO {
    private List<AiChatMessageDTO> messages;
    private Long projectId;
}
