package com.devtoolcopilot.ai.agent.controller;

import com.devtoolcopilot.ai.agent.dto.AiAgentPlanRequestDTO;
import com.devtoolcopilot.ai.agent.dto.AiAgentPlanResponseDTO;
import com.devtoolcopilot.ai.agent.dto.AiAgentCrewRequestDTO;
import com.devtoolcopilot.ai.agent.dto.AiAgentCrewResponseDTO;
import com.devtoolcopilot.ai.agent.dto.AiAgentApplyRequestDTO;
import com.devtoolcopilot.ai.agent.dto.AiAgentApplyResponseDTO;
import com.devtoolcopilot.ai.agent.service.AiAgentApplyService;
import com.devtoolcopilot.ai.agent.service.AiAgentCrewService;
import com.devtoolcopilot.ai.agent.service.AiAgentPlanService;
import com.devtoolcopilot.ai.service.AiChatHistoryService;
import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping({"/ai/agent", "/api/ai/agent"})
public class AiAgentController {
    private final AiAgentPlanService aiAgentPlanService;
    private final AiAgentCrewService aiAgentCrewService;
    private final AiAgentApplyService aiAgentApplyService;
    private final AiChatHistoryService historyService;
    private final ObjectMapper objectMapper;

    public AiAgentController(AiAgentPlanService aiAgentPlanService,
                             AiAgentCrewService aiAgentCrewService,
                             AiAgentApplyService aiAgentApplyService,
                             AiChatHistoryService historyService,
                             ObjectMapper objectMapper) {
        this.aiAgentPlanService = aiAgentPlanService;
        this.aiAgentCrewService = aiAgentCrewService;
        this.aiAgentApplyService = aiAgentApplyService;
        this.historyService = historyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping({"/plan", "/plan/"})
    public R<AiAgentPlanResponseDTO> plan(@RequestBody AiAgentPlanRequestDTO req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            Long projectId = req == null ? null : req.getProjectId();
            String requirement = req == null ? null : req.getRequirement();
            AiAgentPlanResponseDTO dto = aiAgentPlanService.plan(userId, projectId, requirement);
            try {
                historyService.record(userId, projectId, requirement, buildPlanMarkdown(dto, projectId));
            } catch (Exception ignored) {
            }
            return R.ok(dto);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("REQUIREMENT_REQUIRED".equals(msg)) return R.fail(400, "requirement不能为空");
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(msg)) return R.fail(403, "无权限或项目不存在");
            return R.fail(400, "请求参数错误");
        } catch (IllegalStateException e) {
            String msg = e.getMessage();
            if ("DEEPSEEK_UNAUTHORIZED".equals(msg)) return R.fail(502, "DeepSeek API Key无效或无权限");
            if ("DEEPSEEK_RATE_LIMIT".equals(msg)) return R.fail(502, "DeepSeek触发限流或余额不足");
            if ("DEEPSEEK_REQUEST_FAILED".equals(msg)) return R.fail(502, "DeepSeek网络错误或超时");
            if ("AGENT_JSON_INVALID".equals(msg)) return R.fail(502, "AI输出解析失败");
            return R.fail(502, "AI服务调用失败");
        }
    }

    @PostMapping({"/crew", "/crew/"})
    public R<AiAgentCrewResponseDTO> crew(@RequestBody AiAgentCrewRequestDTO req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            Long projectId = req == null ? null : req.getProjectId();
            String requirement = req == null ? null : req.getRequirement();
            AiAgentCrewResponseDTO dto = aiAgentCrewService.run(userId, projectId, requirement);
            try {
                String resp = (dto.getPm() == null ? "" : dto.getPm()) +
                        "\n\n" +
                        (dto.getTechLead() == null ? "" : dto.getTechLead()) +
                        "\n\n" +
                        buildPlanMarkdown(dto.getPlan(), projectId);
                historyService.record(userId, projectId, requirement, resp.trim());
            } catch (Exception ignored) {
            }
            return R.ok(dto);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("REQUIREMENT_REQUIRED".equals(msg)) return R.fail(400, "requirement不能为空");
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(msg)) return R.fail(403, "无权限或项目不存在");
            return R.fail(400, "请求参数错误");
        } catch (IllegalStateException e) {
            String msg = e.getMessage();
            if ("DEEPSEEK_UNAUTHORIZED".equals(msg)) return R.fail(502, "DeepSeek API Key无效或无权限");
            if ("DEEPSEEK_RATE_LIMIT".equals(msg)) return R.fail(502, "DeepSeek触发限流或余额不足");
            if ("DEEPSEEK_REQUEST_FAILED".equals(msg)) return R.fail(502, "DeepSeek网络错误或超时");
            if ("AGENT_JSON_INVALID".equals(msg)) return R.fail(502, "AI输出解析失败");
            return R.fail(502, "AI服务调用失败");
        }
    }

    @PostMapping({"/apply", "/apply/"})
    public R<AiAgentApplyResponseDTO> apply(@RequestBody AiAgentApplyRequestDTO req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            Long projectId = req == null ? null : req.getProjectId();
            AiAgentPlanResponseDTO plan = req == null ? null : req.getPlan();
            AiAgentApplyResponseDTO dto = aiAgentApplyService.apply(userId, projectId, plan);
            return R.ok(dto);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("PROJECT_ID_REQUIRED".equals(msg)) return R.fail(400, "projectId不能为空");
            if ("PLAN_REQUIRED".equals(msg)) return R.fail(400, "plan不能为空");
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(msg)) return R.fail(403, "无权限或项目不存在");
            return R.fail(400, "请求参数错误");
        } catch (IllegalStateException e) {
            String msg = e.getMessage();
            if ("APPLY_CREATE_FAILED".equals(msg)) return R.fail(500, "落地失败：未创建任何任务");
            return R.fail(500, "落地失败");
        } catch (Exception e) {
            return R.fail(500, "服务器错误");
        }
    }

    @PostMapping(value = {"/crew/stream", "/crew/stream/"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter crewStream(@RequestBody AiAgentCrewRequestDTO req) {
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
            Long projectId = req == null ? null : req.getProjectId();
            String requirement = req == null ? null : req.getRequirement();
            try {
                AiAgentCrewResponseDTO dto = aiAgentCrewService.runWithStages(userId, projectId, requirement, (stage, data) -> {
                    try {
                        emitter.send(SseEmitter.event().name(stage).data(data == null ? "" : data));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                try {
                    String resp = (dto.getPm() == null ? "" : dto.getPm()) +
                            "\n\n" +
                            (dto.getTechLead() == null ? "" : dto.getTechLead()) +
                            "\n\n" +
                            buildPlanMarkdown(dto.getPlan(), projectId);
                    historyService.record(userId, projectId, requirement, resp.trim());
                } catch (Exception ignored) {
                }
                emitter.send(SseEmitter.event().name("done").data("ok"));
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data("AI服务调用失败"));
                } catch (Exception ignored) {
                }
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    @PostMapping(value = {"/plan/stream", "/plan/stream/"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter planStream(@RequestBody AiAgentPlanRequestDTO req) {
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
            Long projectId = req == null ? null : req.getProjectId();
            String requirement = req == null ? null : req.getRequirement();
            try {
                AiAgentPlanResponseDTO dto = aiAgentPlanService.plan(userId, projectId, requirement);
                try {
                    historyService.record(userId, projectId, requirement, buildPlanMarkdown(dto, projectId));
                } catch (Exception ignored) {
                }
                String json;
                try {
                    json = objectMapper.writeValueAsString(dto);
                } catch (Exception e) {
                    json = "{}";
                }
                emitter.send(SseEmitter.event().name("result").data(json));
                emitter.send(SseEmitter.event().name("done").data("ok"));
            } catch (IllegalArgumentException e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data("请求参数错误"));
                } catch (Exception ignored) {
                }
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data("AI服务调用失败"));
                } catch (Exception ignored) {
                }
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    private String buildPlanMarkdown(AiAgentPlanResponseDTO dto, Long projectId) {
        if (dto == null) return "## Agent Plan";
        String goal = dto.getGoal() == null ? "" : dto.getGoal().trim();
        StringBuilder sb = new StringBuilder();
        sb.append(goal.isEmpty() ? "## Agent Plan" : "## " + goal);
        if (projectId != null) sb.append("\n\nContext 项目：").append(projectId).append("\n");
        if (dto.getTasks() != null) {
            int i = 1;
            for (var t : dto.getTasks()) {
                if (t == null || t.getTitle() == null || t.getTitle().isBlank()) continue;
                sb.append("\n\n### ").append(i++).append(". ").append(t.getTitle().trim());
                String pr = t.getPriority() == null ? "" : t.getPriority().trim();
                if (!pr.isEmpty()) sb.append(" · ").append(pr);
                String desc = t.getDescription() == null ? "" : t.getDescription().trim();
                if (!desc.isEmpty()) sb.append("\n\n").append(desc);
                if (t.getChecklist() != null && !t.getChecklist().isEmpty()) {
                    sb.append("\n\n验收：");
                    for (String c : t.getChecklist()) {
                        String s = c == null ? "" : c.trim();
                        if (!s.isEmpty()) sb.append("\n- [ ] ").append(s);
                    }
                }
                if (t.getDeliverables() != null && !t.getDeliverables().isEmpty()) {
                    sb.append("\n\n交付物：");
                    for (var d : t.getDeliverables()) {
                        if (d == null || d.getTitle() == null || d.getTitle().isBlank()) continue;
                        String ty = d.getType() == null ? "" : d.getType().trim();
                        if (ty.isEmpty()) ty = "LINK";
                        sb.append("\n- ").append(ty).append(": ").append(d.getTitle().trim());
                    }
                }
            }
        }
        sb.append("\n\n---\n输入 `/apply` 或发送“确认落地”即可落地到当前 Context 项目。");
        return sb.toString();
    }
}
