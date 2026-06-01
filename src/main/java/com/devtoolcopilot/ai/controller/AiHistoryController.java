package com.devtoolcopilot.ai.controller;

import com.devtoolcopilot.ai.dto.AiChatHistoryDTO;
import com.devtoolcopilot.ai.service.AiChatHistoryService;
import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/history")
public class AiHistoryController {
    private final AiChatHistoryService historyService;

    public AiHistoryController(AiChatHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/list")
    public R<List<AiChatHistoryDTO>> list(@RequestParam(required = false) Long projectId,
                                          @RequestParam(required = false) Integer limit) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        try {
            return R.ok(historyService.list(userId, projectId, limit));
        } catch (IllegalArgumentException e) {
            return R.fail(400, "请求参数错误");
        }
    }

    @PostMapping("/delete")
    public R<Integer> delete(@RequestBody Map<String, List<Long>> req) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        try {
            List<Long> ids = req == null ? null : req.get("ids");
            int n = historyService.deleteByIds(userId, ids);
            return R.ok(n);
        } catch (IllegalArgumentException e) {
            return R.fail(400, "请求参数错误");
        }
    }

    @PostMapping("/clear")
    public R<Integer> clear(@RequestBody(required = false) Map<String, Long> req) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        try {
            Long projectId = req == null ? null : req.get("projectId");
            int n = historyService.clear(userId, projectId);
            return R.ok(n);
        } catch (IllegalArgumentException e) {
            return R.fail(400, "请求参数错误");
        }
    }
}
