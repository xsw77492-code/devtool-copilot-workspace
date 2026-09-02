package com.devtoolcopilot.ai.service;

import com.devtoolcopilot.ai.dto.AiChatHistoryDTO;

import java.util.List;

public interface AiChatHistoryService {
    void record(Long userId, Long projectId, String prompt, String response);

    void record(Long userId, Long projectId, String prompt, String response, String type);

    List<AiChatHistoryDTO> list(Long userId, Long projectId, String type, Integer limit);

    int deleteByIds(Long userId, List<Long> ids);

    int clear(Long userId, Long projectId);
}
