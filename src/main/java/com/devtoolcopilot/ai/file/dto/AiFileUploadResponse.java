package com.devtoolcopilot.ai.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiFileUploadResponse {
    private Long projectId;
    private Long assetId;
    private String filename;
    private String contentType;
    private Integer extractedChars;
    private String extractedText;
}

