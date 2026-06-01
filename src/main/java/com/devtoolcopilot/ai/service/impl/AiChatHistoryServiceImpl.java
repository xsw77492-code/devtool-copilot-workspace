package com.devtoolcopilot.ai.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.devtoolcopilot.ai.dto.AiChatHistoryDTO;
import com.devtoolcopilot.ai.entity.AiChatHistory;
import com.devtoolcopilot.ai.mapper.AiChatHistoryMapper;
import com.devtoolcopilot.ai.service.AiChatHistoryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AiChatHistoryServiceImpl extends ServiceImpl<AiChatHistoryMapper, AiChatHistory>
        implements AiChatHistoryService {

    @Override
    public void record(Long userId, Long projectId, String prompt, String response) {
        if (userId == null) {
            throw new IllegalArgumentException("USER_ID_REQUIRED");
        }
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("RESPONSE_REQUIRED");
        }
        AiChatHistory h = new AiChatHistory();
        h.setUserId(userId);
        h.setProjectId(projectId);
        h.setPrompt(prompt == null ? "" : prompt);
        h.setResponse(response);
        this.save(h);
    }

    @Override
    public List<AiChatHistoryDTO> list(Long userId, Long projectId, Integer limit) {
        if (userId == null) {
            throw new IllegalArgumentException("USER_ID_REQUIRED");
        }
        int n = limit == null ? 50 : limit;
        if (n <= 0) n = 50;
        if (n > 200) n = 200;

        var q = Wrappers.<AiChatHistory>lambdaQuery()
                .eq(AiChatHistory::getUserId, userId)
                .orderByDesc(AiChatHistory::getId);
        if (projectId != null) {
            q.eq(AiChatHistory::getProjectId, projectId);
        }
        List<AiChatHistory> list = this.list(q.last("limit " + n));
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public int deleteByIds(Long userId, List<Long> ids) {
        if (userId == null) {
            throw new IllegalArgumentException("USER_ID_REQUIRED");
        }
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return this.baseMapper.delete(Wrappers.<AiChatHistory>lambdaQuery()
                .eq(AiChatHistory::getUserId, userId)
                .in(AiChatHistory::getId, ids));
    }

    @Override
    public int clear(Long userId, Long projectId) {
        if (userId == null) {
            throw new IllegalArgumentException("USER_ID_REQUIRED");
        }
        var q = Wrappers.<AiChatHistory>lambdaQuery().eq(AiChatHistory::getUserId, userId);
        if (projectId != null) {
            q.eq(AiChatHistory::getProjectId, projectId);
        }
        return this.baseMapper.delete(q);
    }

    private AiChatHistoryDTO toDTO(AiChatHistory h) {
        AiChatHistoryDTO dto = new AiChatHistoryDTO();
        dto.setId(h.getId());
        dto.setProjectId(h.getProjectId());
        dto.setPrompt(h.getPrompt());
        dto.setResponse(h.getResponse());
        dto.setCreateTime(h.getCreateTime() == null ? null : h.getCreateTime().toString());
        return dto;
    }
}
