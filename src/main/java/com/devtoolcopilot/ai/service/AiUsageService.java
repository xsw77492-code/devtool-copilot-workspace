package com.devtoolcopilot.ai.service;

import com.devtoolcopilot.ai.entity.AiUsage;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface AiUsageService {
    void record(Long userId, Long projectId, String type, Long promptTokens, Long completionTokens, Long totalTokens);

    /** 最近 periodDays 天按天聚合：date -> {calls, promptTokens, completionTokens, totalTokens} */
    List<Map<String, Object>> dailySummary(Long userId, int periodDays);

    /** 按 type 聚合（全部时间） */
    List<Map<String, Object>> typeSummary(Long userId);

    /** 统计某天起总用量（套餐额度控制用） */
    Map<String, Long> totalSince(Long userId, LocalDate since);
}
