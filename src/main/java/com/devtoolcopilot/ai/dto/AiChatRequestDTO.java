package com.devtoolcopilot.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiChatRequestDTO {
    private List<AiChatMessageDTO> messages;
    private Long projectId;
    /** 记录类型：chat / plan / diagnosis / insight / rootcause / rhythm / code-review */
    private String type;
}
