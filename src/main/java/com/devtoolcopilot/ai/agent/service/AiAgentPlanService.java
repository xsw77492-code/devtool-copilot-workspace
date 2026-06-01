package com.devtoolcopilot.ai.agent.service;

import com.devtoolcopilot.ai.agent.dto.AiAgentPlanResponseDTO;

public interface AiAgentPlanService {
    AiAgentPlanResponseDTO plan(Long userId, Long projectId, String requirement);
}

