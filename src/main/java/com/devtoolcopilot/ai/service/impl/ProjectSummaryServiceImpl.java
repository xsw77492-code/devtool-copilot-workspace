package com.devtoolcopilot.ai.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.ai.client.DeepSeekClient;
import com.devtoolcopilot.ai.prompt.ProjectSummaryPromptBuilder;
import com.devtoolcopilot.ai.service.ProjectSummaryService;
import com.devtoolcopilot.project.entity.Project;
import com.devtoolcopilot.project.mapper.ProjectMapper;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.service.TaskService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

@Service
public class ProjectSummaryServiceImpl implements ProjectSummaryService {
    private final ProjectMapper projectMapper;
    private final TaskService taskService;
    private final ProjectSummaryPromptBuilder promptBuilder;
    private final DeepSeekClient deepSeekClient;
    private final ProjectCollabService projectCollabService;

    public ProjectSummaryServiceImpl(ProjectMapper projectMapper,
                                     TaskService taskService,
                                     ProjectSummaryPromptBuilder promptBuilder,
                                     DeepSeekClient deepSeekClient,
                                     ProjectCollabService projectCollabService) {
        this.projectMapper = projectMapper;
        this.taskService = taskService;
        this.promptBuilder = promptBuilder;
        this.deepSeekClient = deepSeekClient;
        this.projectCollabService = projectCollabService;
    }

    @Override
    public String summarizeProject(Long userId, Long projectId) {
        if (userId == null) {
            throw new IllegalArgumentException("USER_ID_REQUIRED");
        }
        if (projectId == null) {
            throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        }
        projectCollabService.requireMember(userId, projectId);
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new IllegalArgumentException("PROJECT_NOT_FOUND_OR_FORBIDDEN");

        List<Task> tasks = taskService.listByProjectId(userId, projectId);
        String userPrompt = promptBuilder.build(project, tasks);
        return deepSeekClient.chat(promptBuilder.systemPrompt(), userPrompt);
    }

    @Override
    public String summarizeProjectStream(Long userId, Long projectId, Consumer<String> onDelta) {
        if (userId == null) {
            throw new IllegalArgumentException("USER_ID_REQUIRED");
        }
        if (projectId == null) {
            throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        }
        projectCollabService.requireMember(userId, projectId);
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new IllegalArgumentException("PROJECT_NOT_FOUND_OR_FORBIDDEN");

        List<Task> tasks = taskService.listByProjectId(userId, projectId);
        String userPrompt = promptBuilder.build(project, tasks);
        return deepSeekClient.chatStream(promptBuilder.systemPrompt(), List.of(
                new com.devtoolcopilot.ai.client.dto.ChatCompletionRequest.Message("user", userPrompt)
        ), onDelta);
    }
}
