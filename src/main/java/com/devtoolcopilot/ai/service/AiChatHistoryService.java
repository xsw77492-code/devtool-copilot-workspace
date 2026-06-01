package com.devtoolcopilot.ai.service;

import com.devtoolcopilot.ai.dto.AiChatHistoryDTO;

import java.util.List;

public interface AiChatHistoryService {
    void record(Long userId, Long projectId, String prompt, String response);

    List<AiChatHistoryDTO> list(Long userId, Long projectId, Integer limit);

    int deleteByIds(Long userId, List<Long> ids);

    int clear(Long userId, Long projectId);
}
