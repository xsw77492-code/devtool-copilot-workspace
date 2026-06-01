package com.devtoolcopilot.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.ai.agent.dto.AiAgentCrewResponseDTO;
import com.devtoolcopilot.ai.agent.dto.AiAgentPlanResponseDTO;
import com.devtoolcopilot.ai.agent.dto.AiAgentSourceDTO;
import com.devtoolcopilot.ai.agent.service.AiAgentCrewService;
import com.devtoolcopilot.ai.client.DeepSeekClient;
import com.devtoolcopilot.kb.entity.KbExternalDoc;
import com.devtoolcopilot.kb.service.KbExternalDocService;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.task.comment.entity.TaskComment;
import com.devtoolcopilot.task.comment.mapper.TaskCommentMapper;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.mapper.TaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

@Service
public class AiAgentCrewServiceImpl implements AiAgentCrewService {
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;
    private final ProjectCollabService projectCollabService;
    private final TaskMapper taskMapper;
    private final TaskCommentMapper taskCommentMapper;
    private final KbExternalDocService kbExternalDocService;

    public AiAgentCrewServiceImpl(DeepSeekClient deepSeekClient,
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
    public AiAgentCrewResponseDTO run(Long userId, Long projectId, String requirement) {
        return runWithStages(userId, projectId, requirement, null);
    }

    @Override
    public AiAgentCrewResponseDTO runWithStages(Long userId,
                                                Long projectId,
                                                String requirement,
                                                BiConsumer<String, String> onStage) {
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

        PmOut pm = callPm(req, sources);
        if (onStage != null) onStage.accept("pm", formatPm(pm));
        TechOut tech = callTech(pm, sources);
        if (onStage != null) onStage.accept("tech", formatTech(tech));
        AiAgentPlanResponseDTO plan = callExecutor(pm, tech, sources);
        plan.setSources(sources);
        if (onStage != null) {
            try {
                onStage.accept("plan", objectMapper.writeValueAsString(plan));
            } catch (Exception e) {
                onStage.accept("plan", "{}");
            }
        }

        AiAgentCrewResponseDTO out = new AiAgentCrewResponseDTO();
        out.setPm(formatPm(pm));
        out.setTechLead(formatTech(tech));
        out.setPlan(plan);
        return out;
    }

    private PmOut callPm(String requirement, List<AiAgentSourceDTO> sources) {
        String sys = """
                你是 PM Agent。目标：把需求澄清并拆解为可执行的任务列表，不写技术实现。
                用户可能不专业、不完整。你需要基于上下文做合理补全，并把关键假设写进 description（用“假设：”开头），把关键待确认点写进 description（用“待确认：”开头）。
                只输出严格 JSON，不要输出解释文字。
                Schema:
                {"goal":"一句话目标","tasks":[{"title":"<=40字","description":"<=160字，含边界/验收方向"}]}
                约束：tasks 最多 8 条。
                """;
        String user = buildUserPrompt(requirement, sources);
        String raw = deepSeekClient.chat(sys, user);
        return parseJsonOrRepair(raw, PmOut.class);
    }

    private TechOut callTech(PmOut pm, List<AiAgentSourceDTO> sources) {
        String sys = """
                你是 Tech Lead Agent。基于 PM 的任务列表，补全每条任务的优先级、验收清单、交付物。
                信息不完整时做合理假设，但不要胡编外部事实；把假设写在 checklist 的第一条（用“假设：”开头）或 description 中。
                只输出严格 JSON，不要输出解释文字。
                Schema:
                {"tasks":[{"title":"","priority":"HIGH|MEDIUM|LOW","checklist":[""],"deliverables":[{"type":"LINK|DOC|PR","title":"","url":"","content":""}]}]}
                约束：
                - checklist 每条最多 6 项，deliverables 每条最多 3 项
                - deliverables 的 url/content 至少填一个，不能都为空
                """;
        String user = "PM 输出：\n" + toJson(pm) + "\n\n" + buildSourcePrompt(sources);
        String raw = deepSeekClient.chat(sys, user);
        return parseJsonOrRepair(raw, TechOut.class);
    }

    private AiAgentPlanResponseDTO callExecutor(PmOut pm, TechOut tech, List<AiAgentSourceDTO> sources) {
        String sys = """
                你是 Executor Agent。目标：生成可落地的最终计划 JSON（用于创建任务/清单/交付物）。
                用户输入可能不完整：允许做少量合理假设，但必须体现在 description 或 checklist 中（用“假设：”标注），避免产生无法验收的任务。
                只输出严格 JSON，不要输出解释文字。
                Schema:
                {
                  "goal":"一句话目标",
                  "tasks":[
                    {
                      "title":"",
                      "description":"",
                      "priority":"HIGH|MEDIUM|LOW",
                      "checklist":[""],
                      "deliverables":[{"type":"LINK|DOC|PR","title":"","url":"","content":""}],
                      "sources":["T1","C2","D1"]
                    }
                  ]
                }
                约束：
                - tasks 最多 8 条
                - checklist 最多 6 条，deliverables 最多 3 条
                - deliverables 的 url/content 至少填一个，不能都为空
                - sources 只能引用给定的 sourceId（没有可用来源则留空数组）
                """;
        String user = "需求：\n" + pm.getGoal() + "\n\nPM JSON：\n" + toJson(pm) + "\n\nTechLead JSON：\n" + toJson(tech) + "\n\n" + buildSourcePrompt(sources);
        String raw = deepSeekClient.chat(sys, user);
        AiAgentPlanResponseDTO dto = parseJsonOrRepair(raw, AiAgentPlanResponseDTO.class);
        if (dto.getTasks() == null) dto.setTasks(List.of());
        return dto;
    }

    private String formatPm(PmOut pm) {
        StringBuilder sb = new StringBuilder();
        sb.append("### PM Agent\n");
        sb.append(pm.getGoal() == null ? "" : pm.getGoal()).append("\n");
        if (pm.getTasks() != null) {
            int i = 1;
            for (PmTask t : pm.getTasks()) {
                if (t == null || t.getTitle() == null || t.getTitle().isBlank()) continue;
                sb.append("\n").append(i++).append(". ").append(t.getTitle().trim());
                if (t.getDescription() != null && !t.getDescription().isBlank()) {
                    sb.append("\n").append("   ").append(t.getDescription().trim());
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String formatTech(TechOut tech) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Tech Lead Agent\n");
        if (tech.getTasks() != null) {
            int i = 1;
            for (TechTask t : tech.getTasks()) {
                if (t == null || t.getTitle() == null || t.getTitle().isBlank()) continue;
                sb.append("\n").append(i++).append(". ").append(t.getTitle().trim());
                if (t.getPriority() != null && !t.getPriority().isBlank()) sb.append(" · ").append(t.getPriority().trim());
                if (t.getChecklist() != null && !t.getChecklist().isEmpty()) {
                    sb.append("\n   验收：");
                    for (String c : t.getChecklist()) {
                        String s = c == null ? "" : c.trim();
                        if (!s.isEmpty()) sb.append("\n   - ").append(s);
                    }
                }
                if (t.getDeliverables() != null && !t.getDeliverables().isEmpty()) {
                    sb.append("\n   交付物：");
                    for (TechDeliverable d : t.getDeliverables()) {
                        if (d == null) continue;
                        String title = d.getTitle() == null ? "" : d.getTitle().trim();
                        if (title.isEmpty()) continue;
                        String type = d.getType() == null ? "" : d.getType().trim();
                        sb.append("\n   - ").append(type.isEmpty() ? "LINK" : type).append(": ").append(title);
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim();
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

    private String buildUserPrompt(String requirement, List<AiAgentSourceDTO> sources) {
        return "需求：\n" + requirement + "\n\n" + buildSourcePrompt(sources);
    }

    private String buildSourcePrompt(List<AiAgentSourceDTO> sources) {
        if (sources == null || sources.isEmpty()) return "上下文来源：无";
        StringBuilder sb = new StringBuilder();
        sb.append("可引用的上下文来源（按 sourceId 引用）：\n");
        for (AiAgentSourceDTO s : sources) {
            sb.append("[").append(s.getSourceId()).append("] ")
                    .append(s.getType()).append(" ")
                    .append(s.getTitle() == null ? "" : s.getTitle())
                    .append("\n")
                    .append(s.getSnippet() == null ? "" : s.getSnippet())
                    .append("\n\n");
        }
        return sb.toString();
    }

    private String snippet(String text, int max) {
        String s = text == null ? "" : text.trim();
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    private <T> T parseJsonOrRepair(String raw, Class<T> clazz) {
        String json = extractJson(raw);
        if (json != null) {
            try {
                return objectMapper.readValue(json, clazz);
            } catch (Exception ignored) {
            }
        }
        String fixed = deepSeekClient.chat("""
                你是 JSON 修复器。只输出严格 JSON，不要输出解释文字。""", raw == null ? "" : raw);
        String fixedJson = extractJson(fixed);
        if (fixedJson == null) throw new IllegalStateException("AGENT_JSON_INVALID");
        try {
            return objectMapper.readValue(fixedJson, clazz);
        } catch (Exception e) {
            throw new IllegalStateException("AGENT_JSON_INVALID");
        }
    }

    private String extractJson(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        int idx = s.indexOf("{");
        int end = s.lastIndexOf("}");
        if (idx >= 0 && end > idx) return s.substring(idx, end + 1).trim();
        return null;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "";
        }
    }

    @Data
    private static class PmOut {
        private String goal;
        private List<PmTask> tasks;
    }

    @Data
    private static class PmTask {
        private String title;
        private String description;
    }

    @Data
    private static class TechOut {
        private List<TechTask> tasks;
    }

    @Data
    private static class TechTask {
        private String title;
        private String priority;
        private List<String> checklist;
        private List<TechDeliverable> deliverables;
    }

    @Data
    private static class TechDeliverable {
        private String type;
        private String title;
        private String url;
        private String content;
    }
}
