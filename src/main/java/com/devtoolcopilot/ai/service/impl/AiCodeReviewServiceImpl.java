package com.devtoolcopilot.ai.service.impl;

import com.devtoolcopilot.ai.client.DeepSeekClient;
import com.devtoolcopilot.ai.dto.AiCodeReviewResponseDTO;
import com.devtoolcopilot.ai.dto.AiRiskLevel;
import com.devtoolcopilot.ai.prompt.AiCodeReviewPromptBuilder;
import com.devtoolcopilot.ai.service.AiCodeReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class AiCodeReviewServiceImpl implements AiCodeReviewService {
    private final DeepSeekClient deepSeekClient;
    private final AiCodeReviewPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public AiCodeReviewServiceImpl(DeepSeekClient deepSeekClient,
                                   AiCodeReviewPromptBuilder promptBuilder,
                                   ObjectMapper objectMapper) {
        this.deepSeekClient = deepSeekClient;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiCodeReviewResponseDTO review(Long userId, String language, String code) {
        if (userId == null) {
            throw new IllegalArgumentException("USER_ID_REQUIRED");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("CODE_REQUIRED");
        }

        String raw = deepSeekClient.chat(promptBuilder.systemPrompt(), promptBuilder.build(language, code));
        AiCodeReviewResponseDTO dto = parse(raw);
        if (dto.getRiskLevel() == null) {
            dto.setRiskLevel(AiRiskLevel.MEDIUM);
        }
        if (dto.getReport() == null) {
            dto.setReport("");
        }
        return dto;
    }

    private AiCodeReviewResponseDTO parse(String raw) {
        String json = extractJsonObject(raw);
        try {
            return objectMapper.readValue(json, AiCodeReviewResponseDTO.class);
        } catch (Exception e) {
            throw new IllegalStateException("AI_JSON_PARSE_ERROR");
        }
    }

    private static String extractJsonObject(String raw) {
        if (raw == null) {
            throw new IllegalStateException("AI_JSON_NOT_FOUND");
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int first = s.indexOf('\n');
            int last = s.lastIndexOf("```");
            if (first >= 0 && last > first) {
                s = s.substring(first + 1, last).trim();
            }
        }
        int l = s.indexOf('{');
        int r = s.lastIndexOf('}');
        if (l < 0 || r < 0 || r <= l) {
            throw new IllegalStateException("AI_JSON_NOT_FOUND");
        }
        return s.substring(l, r + 1).trim();
    }
}

