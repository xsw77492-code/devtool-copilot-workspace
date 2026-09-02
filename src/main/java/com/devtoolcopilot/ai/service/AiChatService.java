package com.devtoolcopilot.ai.service;

import com.devtoolcopilot.ai.dto.AiChatMessageDTO;

import java.util.List;
import java.util.function.Consumer;

public interface AiChatService {
    String chat(Long userId, List<AiChatMessageDTO> messages);

    String chat(Long userId, Long projectId, List<AiChatMessageDTO> messages);

    String chat(Long userId, Long projectId, List<AiChatMessageDTO> messages, String type);

    String chatStream(Long userId, Long projectId, List<AiChatMessageDTO> messages, Consumer<String> onDelta);

    String chatStream(Long userId, Long projectId, List<AiChatMessageDTO> messages, String type, Consumer<String> onDelta);
}
