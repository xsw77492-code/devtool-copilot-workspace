package com.devtoolcopilot.ai.prompt;

import com.devtoolcopilot.project.entity.Project;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.entity.TaskStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AiChatContextPromptBuilder {
    public String build(Project project, List<Task> tasks) {
        if (project == null) {
            return """
【项目上下文】
当前未选择项目。若用户询问“项目下一步做什么/进度如何”等，请先引导用户选择一个项目或提供 projectId。
""";
        }

        List<Task> safeTasks = tasks == null ? List.of() : tasks;
        Map<TaskStatus, List<Task>> byStatus = safeTasks.stream()
                .collect(Collectors.groupingBy(t -> t.getStatus() == null ? TaskStatus.TODO : t.getStatus()));

        String todo = formatTasks(byStatus.get(TaskStatus.TODO));
        String doing = formatTasks(byStatus.get(TaskStatus.DOING));
        String done = formatTasks(byStatus.get(TaskStatus.DONE));

        long todoCount = byStatus.getOrDefault(TaskStatus.TODO, List.of()).size();
        long doingCount = byStatus.getOrDefault(TaskStatus.DOING, List.of()).size();
        long doneCount = byStatus.getOrDefault(TaskStatus.DONE, List.of()).size();

        return """
【项目上下文】
项目ID：%d
项目名称：%s
项目描述：%s

任务概览：TODO=%d, DOING=%d, DONE=%d

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
                .limit(30)
                .map(t -> "- [" + (t.getStatus() == null ? "TODO" : t.getStatus().name()) + "] " + nvl(t.getTitle()))
                .collect(Collectors.joining("\n"));
    }

    private static String nvl(String s) {
        return (s == null || s.isBlank()) ? "（空）" : s.trim();
    }
}
