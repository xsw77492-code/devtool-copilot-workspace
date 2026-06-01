package com.devtoolcopilot.ai.agent.service.impl;

import com.devtoolcopilot.ai.agent.dto.AiAgentApplyResponseDTO;
import com.devtoolcopilot.ai.agent.dto.AiAgentPlanResponseDTO;
import com.devtoolcopilot.ai.agent.service.AiAgentApplyService;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.realtime.service.RealtimeCollabService;
import com.devtoolcopilot.task.checklist.service.TaskChecklistService;
import com.devtoolcopilot.task.deliverable.service.TaskDeliverableService;
import com.devtoolcopilot.task.service.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class AiAgentApplyServiceImpl implements AiAgentApplyService {
    private final ProjectCollabService projectCollabService;
    private final TaskService taskService;
    private final TaskChecklistService taskChecklistService;
    private final TaskDeliverableService taskDeliverableService;
    private final RealtimeCollabService realtimeCollabService;

    public AiAgentApplyServiceImpl(ProjectCollabService projectCollabService,
                                   TaskService taskService,
                                   TaskChecklistService taskChecklistService,
                                   TaskDeliverableService taskDeliverableService,
                                   RealtimeCollabService realtimeCollabService) {
        this.projectCollabService = projectCollabService;
        this.taskService = taskService;
        this.taskChecklistService = taskChecklistService;
        this.taskDeliverableService = taskDeliverableService;
        this.realtimeCollabService = realtimeCollabService;
    }

    @Override
    @Transactional
    public AiAgentApplyResponseDTO apply(Long userId, Long projectId, AiAgentPlanResponseDTO plan) {
        //region debug-point ai-apply-not-visible/server-report
        try {
            String url = System.getenv("DEBUG_SERVER_URL");
            if (url != null && !url.isBlank()) {
                var conn = (HttpURLConnection) URI.create(url.trim()).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(800);
                conn.setReadTimeout(800);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                String reqJson = "{\"sessionId\":\"ai-apply-not-visible\",\"point\":\"apply.enter\",\"userId\":" + userId + ",\"projectId\":" + projectId + ",\"taskCount\":" + (plan == null || plan.getTasks() == null ? 0 : plan.getTasks().size()) + "}";
                conn.getOutputStream().write(reqJson.getBytes(StandardCharsets.UTF_8));
                conn.getInputStream().close();
            }
        } catch (Exception ignored) {
        }
        //endregion debug-point ai-apply-not-visible/server-report

        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (projectId == null) throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        if (plan == null || plan.getTasks() == null || plan.getTasks().isEmpty()) throw new IllegalArgumentException("PLAN_REQUIRED");

        projectCollabService.requireMember(userId, projectId);

        List<Long> taskIds = new ArrayList<>();
        int requested = 0;
        List<String> failedTitles = new ArrayList<>();
        for (var t : plan.getTasks()) {
            if (t == null) continue;
            String title = t.getTitle() == null ? "" : t.getTitle().trim();
            if (title.isEmpty()) continue;
            requested += 1;
            String desc = t.getDescription() == null ? null : t.getDescription().trim();
            String pr = t.getPriority() == null ? null : t.getPriority().trim();
            Long taskId = taskService.createTask(
                    userId,
                    projectId,
                    title,
                    desc,
                    null,
                    pr,
                    null,
                    null,
                    userId,
                    null,
                    null,
                    null,
                    null,
                    "AI"
            );
            if (taskId == null || taskId <= 0) {
                failedTitles.add(title);
                continue;
            }
            taskIds.add(taskId);

            if (t.getChecklist() != null) {
                int n = 0;
                for (String c : t.getChecklist()) {
                    if (n++ >= 12) break;
                    String s = c == null ? "" : c.trim();
                    if (s.isEmpty()) continue;
                    taskChecklistService.create(userId, taskId, s);
                }
            }
            if (t.getDeliverables() != null) {
                int n = 0;
                for (var d : t.getDeliverables()) {
                    if (n++ >= 8) break;
                    if (d == null) continue;
                    String dtype = d.getType() == null ? "" : d.getType().trim();
                    if (dtype.isEmpty()) dtype = "LINK";
                    String dt = d.getTitle() == null ? "" : d.getTitle().trim();
                    if (dt.isEmpty()) continue;
                    String url = d.getUrl() == null ? null : d.getUrl().trim();
                    String content = d.getContent() == null ? null : d.getContent().trim();
                    if ((url == null || url.isBlank()) && (content == null || content.isBlank())) continue;
                    taskDeliverableService.create(userId, taskId, dtype, dt, url, content);
                }
            }
        }

        if (taskIds.isEmpty()) {
            throw new IllegalStateException("APPLY_CREATE_FAILED");
        }

        //region debug-point ai-apply-not-visible/server-report
        try {
            String url = System.getenv("DEBUG_SERVER_URL");
            if (url != null && !url.isBlank()) {
                var conn = (HttpURLConnection) URI.create(url.trim()).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(800);
                conn.setReadTimeout(800);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                String ids = Base64.getEncoder().encodeToString(taskIds.toString().getBytes(StandardCharsets.UTF_8));
                String reqJson = "{\"sessionId\":\"ai-apply-not-visible\",\"point\":\"apply.created\",\"userId\":" + userId + ",\"projectId\":" + projectId + ",\"taskIdsB64\":\"" + ids + "\"}";
                conn.getOutputStream().write(reqJson.getBytes(StandardCharsets.UTF_8));
                conn.getInputStream().close();
            }
        } catch (Exception ignored) {
        }
        //endregion debug-point ai-apply-not-visible/server-report

        if (realtimeCollabService != null) {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("taskIds", taskIds);
            realtimeCollabService.broadcast(projectId, userId, "AI_APPLY_DONE", payload);
        }

        AiAgentApplyResponseDTO dto = new AiAgentApplyResponseDTO();
        dto.setProjectId(projectId);
        dto.setTaskIds(taskIds);
        dto.setRequestedCount(requested);
        dto.setCreatedCount(taskIds.size());
        dto.setFailedTitles(failedTitles.isEmpty() ? null : failedTitles);

        //region debug-point ai-apply-not-visible/server-report
        try {
            String url = System.getenv("DEBUG_SERVER_URL");
            if (url != null && !url.isBlank()) {
                var conn = (HttpURLConnection) URI.create(url.trim()).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(800);
                conn.setReadTimeout(800);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                String reqJson = "{\"sessionId\":\"ai-apply-not-visible\",\"point\":\"apply.exit\",\"userId\":" + userId + ",\"projectId\":" + projectId + ",\"createdCount\":" + taskIds.size() + "}";
                conn.getOutputStream().write(reqJson.getBytes(StandardCharsets.UTF_8));
                conn.getInputStream().close();
            }
        } catch (Exception ignored) {
        }
        //endregion debug-point ai-apply-not-visible/server-report

        return dto;
    }
}
