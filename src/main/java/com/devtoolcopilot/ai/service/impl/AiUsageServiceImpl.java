package com.devtoolcopilot.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.devtoolcopilot.ai.entity.AiUsage;
import com.devtoolcopilot.ai.mapper.AiUsageMapper;
import com.devtoolcopilot.ai.service.AiUsageService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiUsageServiceImpl extends ServiceImpl<AiUsageMapper, AiUsage> implements AiUsageService {

    @Override
    public void record(Long userId, Long projectId, String type,
                       Long promptTokens, Long completionTokens, Long totalTokens) {
        if (userId == null) return;
        AiUsage u = new AiUsage();
        u.setUserId(userId);
        u.setProjectId(projectId);
        u.setType(type == null || type.isBlank() ? "chat" : type.trim());
        u.setPromptTokens(promptTokens == null ? 0 : promptTokens);
        u.setCompletionTokens(completionTokens == null ? 0 : completionTokens);
        u.setTotalTokens(totalTokens == null ? 0 : totalTokens);
        this.save(u);
    }

    @Override
    public List<Map<String, Object>> dailySummary(Long userId, int periodDays) {
        if (userId == null) return new ArrayList<>();
        int n = periodDays <= 0 ? 7 : Math.min(periodDays, 90);
        LocalDateTime since = LocalDate.now().minusDays(n - 1L).atStartOfDay();
        List<AiUsage> rows = this.lambdaQuery()
                .eq(AiUsage::getUserId, userId)
                .ge(AiUsage::getCreateTime, since)
                .list();
        Map<String, Map<String, Object>> byDate = new HashMap<>();
        for (AiUsage r : rows) {
            String d = r.getCreateTime() == null ? LocalDate.now().toString() : r.getCreateTime().toLocalDate().toString();
            Map<String, Object> m = byDate.computeIfAbsent(d, k -> {
                Map<String, Object> x = new HashMap<>();
                x.put("date", d);
                x.put("calls", 0L);
                x.put("promptTokens", 0L);
                x.put("completionTokens", 0L);
                x.put("totalTokens", 0L);
                return x;
            });
            m.put("calls", ((Number) m.get("calls")).longValue() + 1);
            m.put("promptTokens", ((Number) m.get("promptTokens")).longValue() + (r.getPromptTokens() == null ? 0 : r.getPromptTokens()));
            m.put("completionTokens", ((Number) m.get("completionTokens")).longValue() + (r.getCompletionTokens() == null ? 0 : r.getCompletionTokens()));
            m.put("totalTokens", ((Number) m.get("totalTokens")).longValue() + (r.getTotalTokens() == null ? 0 : r.getTotalTokens()));
        }
        List<Map<String, Object>> result = new ArrayList<>(byDate.values());
        result.sort((a, b) -> String.valueOf(a.get("date")).compareTo(String.valueOf(b.get("date"))));
        return result;
    }

    @Override
    public List<Map<String, Object>> typeSummary(Long userId) {
        if (userId == null) return new ArrayList<>();
        List<AiUsage> rows = this.lambdaQuery().eq(AiUsage::getUserId, userId).list();
        Map<String, Map<String, Object>> byType = new HashMap<>();
        for (AiUsage r : rows) {
            String t = r.getType() == null ? "chat" : r.getType();
            Map<String, Object> m = byType.computeIfAbsent(t, k -> {
                Map<String, Object> x = new HashMap<>();
                x.put("type", t);
                x.put("calls", 0L);
                x.put("totalTokens", 0L);
                return x;
            });
            m.put("calls", ((Number) m.get("calls")).longValue() + 1);
            m.put("totalTokens", ((Number) m.get("totalTokens")).longValue() + (r.getTotalTokens() == null ? 0 : r.getTotalTokens()));
        }
        List<Map<String, Object>> result = new ArrayList<>(byType.values());
        result.sort((a, b) -> Long.compare(((Number) b.get("calls")).longValue(), ((Number) a.get("calls")).longValue()));
        return result;
    }

    @Override
    public Map<String, Long> totalSince(Long userId, LocalDate since) {
        Map<String, Long> result = new HashMap<>();
        result.put("calls", 0L);
        result.put("totalTokens", 0L);
        if (userId == null) return result;
        var q = this.lambdaQuery().eq(AiUsage::getUserId, userId);
        if (since != null) {
            q.ge(AiUsage::getCreateTime, since.atStartOfDay());
        }
        for (AiUsage r : q.list()) {
            result.put("calls", result.get("calls") + 1);
            result.put("totalTokens", result.get("totalTokens") + (r.getTotalTokens() == null ? 0 : r.getTotalTokens()));
        }
        return result;
    }
}
