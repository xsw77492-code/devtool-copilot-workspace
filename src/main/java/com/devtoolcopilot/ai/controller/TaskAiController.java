package com.devtoolcopilot.ai.controller;

import com.devtoolcopilot.ai.dto.TaskPlanDTO;
import com.devtoolcopilot.ai.dto.TaskSplitRequestDTO;
import com.devtoolcopilot.ai.dto.TaskSplitResponseDTO;
import com.devtoolcopilot.ai.service.TaskSplitService;
import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/ai/task")
public class TaskAiController {
    private final TaskSplitService taskSplitService;
    private final ObjectMapper objectMapper;

    public TaskAiController(TaskSplitService taskSplitService, ObjectMapper objectMapper) {
        this.taskSplitService = taskSplitService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/split")
    public R<List<TaskPlanDTO>> split(@RequestBody TaskSplitRequestDTO req) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        try {
            String requirement = req == null ? null : req.getRequirement();
            TaskSplitResponseDTO resp = taskSplitService.split(userId, requirement);
            return R.ok(resp.getPlans());
        } catch (IllegalArgumentException e) {
            if ("REQUIREMENT_REQUIRED".equals(e.getMessage())) {
                return R.fail(400, "requirement不能为空");
            }
            return R.fail(400, "任务拆解失败");
        } catch (IllegalStateException e) {
            String msg = e.getMessage();
            if ("DEEPSEEK_UNAUTHORIZED".equals(msg)) {
                return R.fail(502, "DeepSeek API Key无效或无权限");
            }
            if ("DEEPSEEK_RATE_LIMIT".equals(msg)) {
                return R.fail(502, "DeepSeek触发限流或余额不足");
            }
            if ("DEEPSEEK_REQUEST_FAILED".equals(msg)) {
                return R.fail(502, "DeepSeek网络错误或超时");
            }
            if ("AI_JSON_PARSE_ERROR".equals(msg) || "AI_JSON_NOT_FOUND".equals(msg)) {
                return R.fail(502, "AI输出解析失败");
            }
            return R.fail(502, "AI服务调用失败");
        }
    }

    @PostMapping(value = "/split/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter splitStream(@RequestBody TaskSplitRequestDTO req) {
        Long userId = UserContext.getUserId();
        SseEmitter emitter = new SseEmitter(0L);
        if (userId == null) {
            CompletableFuture.runAsync(() -> {
                try {
                    emitter.send(SseEmitter.event().name("error").data("未登录"));
                } catch (Exception ignored) {
                } finally {
                    emitter.complete();
                }
            });
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String requirement = req == null ? null : req.getRequirement();
                TaskSplitResponseDTO resp = taskSplitService.splitStream(userId, requirement, (delta) -> {
                    try {
                        emitter.send(SseEmitter.event().name("delta").data(delta));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                String json = objectMapper.writeValueAsString(resp.getPlans());
                emitter.send(SseEmitter.event().name("result").data(json));
                emitter.send(SseEmitter.event().name("done").data("1"));
                emitter.complete();
            } catch (IllegalArgumentException e) {
                String msg = e.getMessage();
                String m = "任务拆解失败";
                if ("REQUIREMENT_REQUIRED".equals(msg)) m = "requirement不能为空";
                try {
                    emitter.send(SseEmitter.event().name("error").data(m));
                } catch (Exception ignored) {
                } finally {
                    emitter.complete();
                }
            } catch (IllegalStateException e) {
                String msg = e.getMessage();
                String m = "AI服务调用失败";
                if ("DEEPSEEK_UNAUTHORIZED".equals(msg)) m = "DeepSeek API Key无效或无权限";
                else if ("DEEPSEEK_RATE_LIMIT".equals(msg)) m = "DeepSeek触发限流或余额不足";
                else if ("DEEPSEEK_REQUEST_FAILED".equals(msg)) m = "DeepSeek网络错误或超时";
                else if ("AI_JSON_PARSE_ERROR".equals(msg) || "AI_JSON_NOT_FOUND".equals(msg)) m = "AI输出解析失败";
                try {
                    emitter.send(SseEmitter.event().name("error").data(m));
                } catch (Exception ignored) {
                } finally {
                    emitter.complete();
                }
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data("任务拆解失败"));
                } catch (Exception ignored) {
                } finally {
                    emitter.complete();
                }
            }
        });

        return emitter;
    }
}
