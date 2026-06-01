package com.devtoolcopilot.ai.agent.service;

import com.devtoolcopilot.ai.agent.dto.AiAgentCrewResponseDTO;

import java.util.function.BiConsumer;

public interface AiAgentCrewService {
    AiAgentCrewResponseDTO run(Long userId, Long projectId, String requirement);

    AiAgentCrewResponseDTO runWithStages(Long userId,
                                         Long projectId,
                                         String requirement,
                                         BiConsumer<String, String> onStage);
}
