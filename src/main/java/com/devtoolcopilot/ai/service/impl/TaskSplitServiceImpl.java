package com.devtoolcopilot.ai.service.impl;

import com.devtoolcopilot.ai.client.DeepSeekClient;
import com.devtoolcopilot.ai.dto.TaskPlanDTO;
import com.devtoolcopilot.ai.dto.TaskPriority;
import com.devtoolcopilot.ai.dto.TaskSplitResponseDTO;
import com.devtoolcopilot.ai.prompt.TaskSplitPromptBuilder;
import com.devtoolcopilot.ai.service.TaskSplitService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

@Service
public class TaskSplitServiceImpl implements TaskSplitService {
    private final DeepSeekClient deepSeekClient;
    private final TaskSplitPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public TaskSplitServiceImpl(DeepSeekClient deepSeekClient,
                                TaskSplitPromptBuilder promptBuilder,
                                ObjectMapper objectMapper) {
        this.deepSeekClient = deepSeekClient;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public TaskSplitResponseDTO split(Long userId, String requirement) {
        if (userId == null) {
            throw new IllegalArgumentException("USER_ID_REQUIRED");
        }
        if (requirement == null || requirement.isBlank()) {
            throw new IllegalArgumentException("REQUIREMENT_REQUIRED");
        }

        String raw = deepSeekClient.chat(promptBuilder.systemPrompt(), promptBuilder.build(requirement));
        List<TaskPlanDTO> plans = parsePlans(raw);
        return new TaskSplitResponseDTO(plans);
    }

    @Override
    public TaskSplitResponseDTO splitStream(Long userId, String requirement, Consumer<String> onDelta) {
        if (userId == null) {
            throw new IllegalArgumentException("USER_ID_REQUIRED");
        }
        if (requirement == null || requirement.isBlank()) {
            throw new IllegalArgumentException("REQUIREMENT_REQUIRED");
        }

        String raw = deepSeekClient.chatStream(promptBuilder.systemPrompt(), List.of(
                new com.devtoolcopilot.ai.client.dto.ChatCompletionRequest.Message("user", promptBuilder.build(requirement))
        ), onDelta);
        List<TaskPlanDTO> plans = parsePlans(raw);
        return new TaskSplitResponseDTO(plans);
    }

    private List<TaskPlanDTO> parsePlans(String raw) {
        String json = extractJsonArray(raw);
        List<TaskPlanDTO> list;
        try {
            list = objectMapper.readValue(json, new TypeReference<List<TaskPlanDTO>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AI_JSON_PARSE_ERROR");
        }
        if (list == null) {
            throw new IllegalStateException("AI_JSON_EMPTY");
        }

        List<TaskPlanDTO> cleaned = new ArrayList<>();
        for (TaskPlanDTO it : list) {
            if (it == null) continue;
            if (it.getTitle() == null || it.getTitle().isBlank()) continue;
            if (it.getOrder() == null || it.getOrder() <= 0) continue;
            if (it.getDescription() != null) {
                it.setDescription(it.getDescription().trim());
            }
            if (it.getPriority() == null) {
                it.setPriority(TaskPriority.MEDIUM);
            }
            cleaned.add(it);
        }
        if (cleaned.isEmpty()) {
            throw new IllegalStateException("AI_JSON_EMPTY");
        }
        cleaned.sort(Comparator.comparing(TaskPlanDTO::getOrder));
        return cleaned;
    }

    private static String extractJsonArray(String raw) {
        if (raw == null) {
            throw new IllegalStateException("AI_JSON_EMPTY");
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int first = s.indexOf('\n');
            int last = s.lastIndexOf("```");
            if (first > 0 && last > first) {
                s = s.substring(first + 1, last).trim();
            }
        }
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            throw new IllegalStateException("AI_JSON_NOT_FOUND");
        }
        return s.substring(start, end + 1).trim();
    }
}
