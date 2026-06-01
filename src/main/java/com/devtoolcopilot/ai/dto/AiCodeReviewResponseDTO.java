package com.devtoolcopilot.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiCodeReviewResponseDTO {
    private AiRiskLevel riskLevel;
    private String report;

    public AiCodeReviewResponseDTO() {
    }
}
