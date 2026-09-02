package com.devtoolcopilot.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.ai.agent.dto.*;
import com.devtoolcopilot.ai.agent.service.AiAgentPlanService;
import com.devtoolcopilot.ai.client.DeepSeekClient;
import com.devtoolcopilot.kb.entity.KbExternalDoc;
import com.devtoolcopilot.kb.service.KbExternalDocService;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.task.comment.entity.TaskComment;
import com.devtoolcopilot.task.comment.mapper.TaskCommentMapper;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.mapper.TaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiAgentPlanServiceImpl implements AiAgentPlanService {
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;
    private final ProjectCollabService projectCollabService;
    private final TaskMapper taskMapper;
    private final TaskCommentMapper taskCommentMapper;
    private final KbExternalDocService kbExternalDocService;

    public AiAgentPlanServiceImpl(DeepSeekClient deepSeekClient,
                                  ObjectMapper objectMapper,
                                  ProjectCollabService projectCollabService,
                                  TaskMapper taskMapper,
                                  TaskCommentMapper taskCommentMapper,
                                  KbExternalDocService kbExternalDocService) {
        this.deepSeekClient = deepSeekClient;
        this.objectMapper = objectMapper;
        this.projectCollabService = projectCollabService;
        this.taskMapper = taskMapper;
        this.taskCommentMapper = taskCommentMapper;
        this.kbExternalDocService = kbExternalDocService;
    }

    @Override
    public AiAgentPlanResponseDTO plan(Long userId, Long projectId, String requirement) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (requirement == null || requirement.isBlank()) throw new IllegalArgumentException("REQUIREMENT_REQUIRED");
        String req = requirement.trim();

        List<AiAgentSourceDTO> sources = new ArrayList<>();
        if (projectId != null) {
            projectCollabService.requireMember(userId, projectId);
            sources.addAll(loadTaskSources(projectId));
            sources.addAll(loadCommentSources(projectId));
            sources.addAll(loadExternalDocSources(userId, projectId, req));
        }

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(req, sources);
        String raw = deepSeekClient.chat(systemPrompt, userPrompt);

        AiAgentPlanResponseDTO dto = parseJsonOrRepair(raw, AiAgentPlanResponseDTO.class);
        if (dto.getTasks() == null) dto.setTasks(List.of());
        dto.setSources(sources);
        normalize(dto);
        return dto;
    }

    private List<AiAgentSourceDTO> loadTaskSources(Long projectId) {
        List<Task> list = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .eq(Task::getProjectId, projectId)
                .orderByDesc(Task::getUpdatedAt)
                .orderByDesc(Task::getId)
                .last("LIMIT 6"));
        List<AiAgentSourceDTO> out = new ArrayList<>();
        int i = 1;
        for (Task t : list) {
            if (t == null) continue;
            AiAgentSourceDTO s = new AiAgentSourceDTO();
            s.setSourceId("T" + i++);
            s.setType("TASK");
            s.setRefId(t.getId());
            s.setTitle(t.getTitle());
            s.setSnippet(snippet((t.getDescription() == null ? "" : t.getDescription()) + "\n" + (t.getAcceptanceCriteria() == null ? "" : t.getAcceptanceCriteria()), 260));
            out.add(s);
        }
        return out;
    }

    private List<AiAgentSourceDTO> loadCommentSources(Long projectId) {
        List<TaskComment> list = taskCommentMapper.selectList(Wrappers.<TaskComment>lambdaQuery()
                .eq(TaskComment::getProjectId, projectId)
                .orderByDesc(TaskComment::getId)
                .last("LIMIT 8"));
        List<AiAgentSourceDTO> out = new ArrayList<>();
        int i = 1;
        for (TaskComment c : list) {
            if (c == null) continue;
            AiAgentSourceDTO s = new AiAgentSourceDTO();
            s.setSourceId("C" + i++);
            s.setType("COMMENT");
            s.setRefId(c.getId());
            s.setTitle("taskId=" + c.getTaskId());
            s.setSnippet(snippet(c.getContent(), 220));
            out.add(s);
        }
        return out;
    }

    private List<AiAgentSourceDTO> loadExternalDocSources(Long userId, Long projectId, String q) {
        List<KbExternalDoc> docs = kbExternalDocService.search(userId, projectId, q, 5);
        List<AiAgentSourceDTO> out = new ArrayList<>();
        int i = 1;
        for (KbExternalDoc d : docs) {
            if (d == null) continue;
            AiAgentSourceDTO s = new AiAgentSourceDTO();
            s.setSourceId("D" + i++);
            s.setType("DOC");
            s.setRefId(d.getId());
            s.setTitle(d.getTitle());
            s.setSnippet(snippet(d.getContent(), 240));
            out.add(s);
        }
        return out;
    }

    private String buildSystemPrompt() {
        return """
                你是 DevTool Copilot 内置的 AI Agent（项目经理 + Tech Lead）。目标：把用户的需求变成可执行的任务闭环。
                用户输入可能不完整、不专业。你需要基于上下文做合理补全，但不要编造外部事实。
                信息不完整时：
                - 把关键假设写进 description（用“假设：”开头）
                - 把关键待确认点写进 checklist 的第一条（用“待确认：”开头）
                只输出严格 JSON，不要输出 Markdown、不要输出解释文字、不要加代码块标记。
                JSON Schema:
                {
                  "goal": "一句话目标",
                  "tasks": [
                    {
                      "title": "任务标题(<=40字)",
                      "description": "任务说明(<=320字，含关键点/边界/实现提示/风险；必要时写“假设：...”)",
                      "priority": "HIGH|MEDIUM|LOW",
                      "checklist": ["验收点1", "验收点2（必要时第一条为“待确认：...”）"],
                      "deliverables": [{"type":"LINK|DOC|PR","title":"交付物标题","url":"","content":""}],
                      "sources": ["T1","C2","D1"]
                    }
                  ]
                }
                约束：
                - tasks 最多 8 条，每条 checklist 最多 6 条，deliverables 最多 3 条
                - deliverables 的 url/content 至少填一个，不能都为空
                - sources 只能引用给定的 sourceId（如果没有可用来源则留空数组）
                """;
    }

    private String buildUserPrompt(String requirement, List<AiAgentSourceDTO> sources) {
        StringBuilder sb = new StringBuilder();
        sb.append("需求：\n").append(requirement).append("\n\n");
        if (sources != null && !sources.isEmpty()) {
            sb.append("可引用的上下文来源（按 sourceId 引用）：\n");
            for (AiAgentSourceDTO s : sources) {
                sb.append("[").append(s.getSourceId()).append("] ")
                        .append(s.getType()).append(" ")
                        .append(s.getTitle() == null ? "" : s.getTitle())
                        .append("\n")
                        .append(s.getSnippet() == null ? "" : s.getSnippet())
                        .append("\n\n");
            }
        } else {
            sb.append("上下文来源：无\n");
        }
        return sb.toString();
    }

    private String extractJson(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.startsWith("```")) {
            int idx = s.indexOf("{");
            int end = s.lastIndexOf("}");
            if (idx >= 0 && end > idx) return s.substring(idx, end + 1).trim();
        }
        int idx = s.indexOf("{");
        int end = s.lastIndexOf("}");
        if (idx >= 0 && end > idx) return s.substring(idx, end + 1).trim();
        return null;
    }

    private <T> T parseJsonOrRepair(String raw, Class<T> clazz) {
        String json = extractJson(raw);
        if (json != null && !json.isBlank()) {
            try {
                return objectMapper.readValue(json, clazz);
            } catch (Exception ignored) {
            }
        }
        String fixed = deepSeekClient.chat("""
                你是 JSON 修复器。只输出严格 JSON，不要输出解释文字。""", raw == null ? "" : raw);
        String fixedJson = extractJson(fixed);
        if (fixedJson == null || fixedJson.isBlank()) throw new IllegalStateException("AGENT_JSON_INVALID");
        try {
            return objectMapper.readValue(fixedJson, clazz);
        } catch (Exception e) {
            throw new IllegalStateException("AGENT_JSON_INVALID");
        }
    }

    private String snippet(String text, int max) {
        String s = text == null ? "" : text.trim();
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    private void normalize(AiAgentPlanResponseDTO dto) {
        if (dto == null) return;
        if (dto.getGoal() != null) dto.setGoal(dto.getGoal().trim());
        if (dto.getTasks() == null) dto.setTasks(List.of());
        List<AiAgentTaskDTO> tasks = new ArrayList<>();
        for (AiAgentTaskDTO t : dto.getTasks()) {
            if (t == null) continue;
            if (t.getTitle() != null) t.setTitle(t.getTitle().trim());
            if (t.getDescription() != null) t.setDescription(t.getDescription().trim());
            if (t.getPriority() != null) t.setPriority(t.getPriority().trim().toUpperCase());
            if (t.getChecklist() == null) t.setChecklist(List.of());
            if (t.getDeliverables() == null) t.setDeliverables(List.of());
            if (t.getSources() == null) t.setSources(List.of());
            tasks.add(t);
        }
        dto.setTasks(tasks);
    }
}
