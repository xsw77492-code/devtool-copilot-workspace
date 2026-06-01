package com.devtoolcopilot.ai.service;

import com.devtoolcopilot.ai.dto.AiCodeReviewResponseDTO;

public interface AiCodeReviewService {
    AiCodeReviewResponseDTO review(Long userId, String language, String code);
}
