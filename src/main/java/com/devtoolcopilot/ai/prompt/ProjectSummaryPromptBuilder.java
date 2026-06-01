package com.devtoolcopilot.ai.prompt;

import com.devtoolcopilot.project.entity.Project;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.entity.TaskStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ProjectSummaryPromptBuilder {
    public String systemPrompt() {
        return "你是资深后端架构师与项目经理，擅长从软件工程视角总结项目状态并给出可执行建议。输出要求：中文、结构化、小标题清晰、避免空话。";
    }

    public String build(Project project, List<Task> tasks) {
        Map<TaskStatus, List<Task>> byStatus = tasks.stream()
                .collect(Collectors.groupingBy(t -> t.getStatus() == null ? TaskStatus.TODO : t.getStatus()));

        String todo = formatTasks(byStatus.get(TaskStatus.TODO));
        String doing = formatTasks(byStatus.get(TaskStatus.DOING));
        String done = formatTasks(byStatus.get(TaskStatus.DONE));

        long todoCount = byStatus.getOrDefault(TaskStatus.TODO, List.of()).size();
        long doingCount = byStatus.getOrDefault(TaskStatus.DOING, List.of()).size();
        long doneCount = byStatus.getOrDefault(TaskStatus.DONE, List.of()).size();

        return """
请基于以下项目数据生成《项目总结报告》，必须包含以下部分：
1) 项目整体描述
2) 当前任务进度分析（TODO/DOING/DONE，含数量与关键任务）
3) 存在的问题（从需求、进度、质量、协作、风险角度）
4) 优化建议（可执行、可落地）
5) 下一步开发建议（按优先级给出）

【项目信息】
- 项目ID：%d
- 项目名称：%s
- 项目描述：%s

【任务概览】
- TODO：%d
- DOING：%d
- DONE：%d

【任务明细】
TODO:
%s

DOING:
%s

DONE:
%s
""".formatted(
                project.getId(),
                nvl(project.getName()),
                nvl(project.getDescription()),
                todoCount,
                doingCount,
                doneCount,
                todo,
                doing,
                done
        );
    }

    private static String formatTasks(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return "- （无）";
        }
        return tasks.stream()
                .map(t -> "- [" + (t.getStatus() == null ? "TODO" : t.getStatus().name()) + "] " + nvl(t.getTitle()))
                .collect(Collectors.joining("\n"));
    }

    private static String nvl(String s) {
        return (s == null || s.isBlank()) ? "（空）" : s;
    }
}
