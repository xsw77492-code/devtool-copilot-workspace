package com.devtoolcopilot.ai.controller;

import com.devtoolcopilot.ai.config.DeepSeekProperties;
import com.devtoolcopilot.ai.service.AiUsageService;
import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/usage")
public class AiUsageController {
    private final AiUsageService usageService;
    private final DeepSeekProperties properties;

    public AiUsageController(AiUsageService usageService, DeepSeekProperties properties) {
        this.usageService = usageService;
        this.properties = properties;
    }

    /** 最近 N 天按天用量（调用次数 / prompt / completion / total tokens） */
    @GetMapping("/daily")
    public R<List<Map<String, Object>>> daily(@RequestParam(defaultValue = "7") int days) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        return R.ok(usageService.dailySummary(userId, days));
    }

    /** 按功能类型聚合用量 */
    @GetMapping("/types")
    public R<List<Map<String, Object>>> types() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        return R.ok(usageService.typeSummary(userId));
    }

    /** 套餐额度：本月已用 / 配额 / 今日调用 / 今日上限 */
    @GetMapping("/quota")
    public R<Map<String, Object>> quota() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        LocalDate firstDay = LocalDate.now().withDayOfMonth(1);
        Map<String, Long> month = usageService.totalSince(userId, firstDay);
        Map<String, Long> today = usageService.totalSince(userId, LocalDate.now());

        Long monthlyQuota = properties.getMonthlyTokenQuota();
        Long dailyLimit = properties.getDailyCallLimit();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("monthlyUsedTokens", month.getOrDefault("totalTokens", 0L));
        out.put("monthlyQuota", monthlyQuota == null ? 0L : monthlyQuota);
        out.put("todayCalls", today.getOrDefault("calls", 0L));
        out.put("dailyCallLimit", dailyLimit == null ? 0L : dailyLimit);
        out.put("monthlyExceeded", monthlyQuota != null && monthlyQuota > 0 && month.getOrDefault("totalTokens", 0L) >= monthlyQuota);
        return R.ok(out);
    }
}
