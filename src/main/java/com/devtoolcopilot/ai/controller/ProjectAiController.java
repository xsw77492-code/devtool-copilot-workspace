package com.devtoolcopilot.ai.controller;

import com.devtoolcopilot.ai.dto.ProjectSummaryRequest;
import com.devtoolcopilot.ai.dto.ProjectSummaryResponse;
import com.devtoolcopilot.ai.service.ProjectSummaryService;
import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/ai/project")
public class ProjectAiController {
    private final ProjectSummaryService projectSummaryService;

    public ProjectAiController(ProjectSummaryService projectSummaryService) {
        this.projectSummaryService = projectSummaryService;
    }

    @PostMapping("/summary")
    public R<ProjectSummaryResponse> summary(@RequestBody ProjectSummaryRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        try {
            String report = projectSummaryService.summarizeProject(userId, req.getProjectId());
            return R.ok(new ProjectSummaryResponse(req.getProjectId(), report));
        } catch (IllegalArgumentException e) {
            if ("PROJECT_ID_REQUIRED".equals(e.getMessage())) {
                return R.fail(400, "projectId不能为空");
            }
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) {
                return R.fail(403, "无权限或项目不存在");
            }
            return R.fail(400, "生成总结失败");
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
            return R.fail(502, "AI服务调用失败");
        }
    }

    @PostMapping(value = "/summary/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter summaryStream(@RequestBody ProjectSummaryRequest req) {
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
                Long projectId = req == null ? null : req.getProjectId();
                projectSummaryService.summarizeProjectStream(userId, projectId, (delta) -> {
                    try {
                        emitter.send(SseEmitter.event().name("delta").data(delta));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                emitter.send(SseEmitter.event().name("done").data("1"));
                emitter.complete();
            } catch (IllegalArgumentException e) {
                String msg = e.getMessage();
                String m = "生成总结失败";
                if ("PROJECT_ID_REQUIRED".equals(msg)) m = "projectId不能为空";
                else if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(msg)) m = "无权限或项目不存在";
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
                try {
                    emitter.send(SseEmitter.event().name("error").data(m));
                } catch (Exception ignored) {
                } finally {
                    emitter.complete();
                }
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data("生成总结失败"));
                } catch (Exception ignored) {
                } finally {
                    emitter.complete();
                }
            }
        });

        return emitter;
    }
}
