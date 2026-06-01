package com.devtoolcopilot.ai.controller;

import com.devtoolcopilot.ai.dto.AiChatRequestDTO;
import com.devtoolcopilot.ai.dto.AiChatResponseDTO;
import com.devtoolcopilot.ai.service.AiChatService;
import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/ai")
public class ChatAiController {
    private final AiChatService aiChatService;

    public ChatAiController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/chat")
    public R<AiChatResponseDTO> chat(@RequestBody AiChatRequestDTO req) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        try {
            Long projectId = req == null ? null : req.getProjectId();
            String reply = aiChatService.chat(userId, projectId, req == null ? null : req.getMessages());
            return R.ok(new AiChatResponseDTO(reply));
        } catch (IllegalArgumentException e) {
            if ("MESSAGES_REQUIRED".equals(e.getMessage())) {
                return R.fail(400, "messages不能为空");
            }
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) {
                return R.fail(403, "无权限或项目不存在");
            }
            return R.fail(400, "请求参数错误");
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

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody AiChatRequestDTO req) {
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
                aiChatService.chatStream(userId, projectId, req == null ? null : req.getMessages(), (delta) -> {
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
                String m = "请求参数错误";
                if ("MESSAGES_REQUIRED".equals(msg)) m = "messages不能为空";
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
                    emitter.send(SseEmitter.event().name("error").data("AI服务调用失败"));
                } catch (Exception ignored) {
                } finally {
                    emitter.complete();
                }
            }
        });

        return emitter;
    }
}
