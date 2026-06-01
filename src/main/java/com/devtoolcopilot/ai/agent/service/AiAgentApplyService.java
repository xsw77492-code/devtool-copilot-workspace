package com.devtoolcopilot.ai.agent.service;

import com.devtoolcopilot.ai.agent.dto.AiAgentApplyResponseDTO;
import com.devtoolcopilot.ai.agent.dto.AiAgentPlanResponseDTO;

public interface AiAgentApplyService {
    AiAgentApplyResponseDTO apply(Long userId, Long projectId, AiAgentPlanResponseDTO plan);
}

