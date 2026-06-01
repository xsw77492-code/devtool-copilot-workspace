package com.devtoolcopilot.ai.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.ai.client.DeepSeekClient;
import com.devtoolcopilot.ai.client.dto.ChatCompletionRequest;
import com.devtoolcopilot.ai.dto.AiChatMessageDTO;
import com.devtoolcopilot.ai.prompt.AiChatContextPromptBuilder;
import com.devtoolcopilot.ai.prompt.AiChatPromptBuilder;
import com.devtoolcopilot.ai.service.AiChatHistoryService;
import com.devtoolcopilot.ai.service.AiChatService;
import com.devtoolcopilot.project.entity.Project;
import com.devtoolcopilot.project.mapper.ProjectMapper;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.service.TaskService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
public class AiChatServiceImpl implements AiChatService {
    private final DeepSeekClient deepSeekClient;
    private final AiChatPromptBuilder promptBuilder;
    private final AiChatContextPromptBuilder contextPromptBuilder;
    private final ProjectMapper projectMapper;
    private final TaskService taskService;
    private final AiChatHistoryService historyService;
    private final ProjectCollabService projectCollabService;

    public AiChatServiceImpl(DeepSeekClient deepSeekClient,
                             AiChatPromptBuilder promptBuilder,
                             AiChatContextPromptBuilder contextPromptBuilder,
                             ProjectMapper projectMapper,
                             TaskService taskService,
                             AiChatHistoryService historyService,
                             ProjectCollabService projectCollabService) {
        this.deepSeekClient = deepSeekClient;
        this.promptBuilder = promptBuilder;
        this.contextPromptBuilder = contextPromptBuilder;
        this.projectMapper = projectMapper;
        this.taskService = taskService;
        this.historyService = historyService;
        this.projectCollabService = projectCollabService;
    }

    @Override
    public String chat(Long userId, List<AiChatMessageDTO> messages) {
        return chat(userId, null, messages);
    }

    public String chat(Long userId, Long projectId, List<AiChatMessageDTO> messages) {
        if (userId == null) {
            throw new IllegalArgumentException("USER_ID_REQUIRED");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("MESSAGES_REQUIRED");
        }

        Project project = null;
        List<Task> tasks = List.of();
        if (projectId != null) {
            projectCollabService.requireMember(userId, projectId);
            project = projectMapper.selectById(projectId);
            if (project == null) throw new IllegalArgumentException("PROJECT_NOT_FOUND_OR_FORBIDDEN");
            tasks = taskService.listByProjectId(userId, projectId);
        }

        String context = contextPromptBuilder.build(project, tasks);
        String systemPrompt = promptBuilder.systemPrompt(context);

        List<ChatCompletionRequest.Message> reqMessages = new ArrayList<>();
        for (AiChatMessageDTO m : messages) {
            if (m == null) continue;
            String role = m.getRole();
            String content = m.getContent();
            if (role == null || role.isBlank()) continue;
            if (content == null || content.isBlank()) continue;
            String r = role.trim().toLowerCase();
            if (!"user".equals(r) && !"assistant".equals(r) && !"system".equals(r)) {
                continue;
            }
            reqMessages.add(new ChatCompletionRequest.Message(r, content.trim()));
        }

        if (reqMessages.isEmpty()) {
            throw new IllegalArgumentException("MESSAGES_REQUIRED");
        }

        int max = 20;
        if (reqMessages.size() > max) {
            reqMessages = reqMessages.subList(reqMessages.size() - max, reqMessages.size());
        }

        String reply = deepSeekClient.chat(systemPrompt, reqMessages);
        historyService.record(userId, projectId, lastUserPrompt(reqMessages), reply);
        return reply;
    }

    @Override
    public String chatStream(Long userId,
                             Long projectId,
                             List<AiChatMessageDTO> messages,
                             Consumer<String> onDelta) {
        if (userId == null) {
            throw new IllegalArgumentException("USER_ID_REQUIRED");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("MESSAGES_REQUIRED");
        }

        Project project = null;
        List<Task> tasks = List.of();
        if (projectId != null) {
            projectCollabService.requireMember(userId, projectId);
            project = projectMapper.selectById(projectId);
            if (project == null) throw new IllegalArgumentException("PROJECT_NOT_FOUND_OR_FORBIDDEN");
            tasks = taskService.listByProjectId(userId, projectId);
        }

        String context = contextPromptBuilder.build(project, tasks);
        String systemPrompt = promptBuilder.systemPrompt(context);

        List<ChatCompletionRequest.Message> reqMessages = new ArrayList<>();
        for (AiChatMessageDTO m : messages) {
            if (m == null) continue;
            String role = m.getRole();
            String content = m.getContent();
            if (role == null || role.isBlank()) continue;
            if (content == null || content.isBlank()) continue;
            String r = role.trim().toLowerCase();
            if (!"user".equals(r) && !"assistant".equals(r) && !"system".equals(r)) {
                continue;
            }
            reqMessages.add(new ChatCompletionRequest.Message(r, content.trim()));
        }

        if (reqMessages.isEmpty()) {
            throw new IllegalArgumentException("MESSAGES_REQUIRED");
        }

        int max = 20;
        if (reqMessages.size() > max) {
            reqMessages = reqMessages.subList(reqMessages.size() - max, reqMessages.size());
        }

        String reply = deepSeekClient.chatStream(systemPrompt, reqMessages, onDelta);
        historyService.record(userId, projectId, lastUserPrompt(reqMessages), reply);
        return reply;
    }

    private static String lastUserPrompt(List<ChatCompletionRequest.Message> reqMessages) {
        if (reqMessages == null || reqMessages.isEmpty()) {
            return "";
        }
        for (int i = reqMessages.size() - 1; i >= 0; i--) {
            ChatCompletionRequest.Message m = reqMessages.get(i);
            if (m == null) continue;
            if ("user".equals(m.getRole())) {
                return m.getContent() == null ? "" : m.getContent();
            }
        }
        return "";
    }
}
