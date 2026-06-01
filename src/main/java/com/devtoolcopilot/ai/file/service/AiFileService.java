package com.devtoolcopilot.ai.file.service;

import com.devtoolcopilot.ai.file.dto.AiFileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface AiFileService {
    AiFileUploadResponse uploadAndExtract(Long userId, Long projectId, MultipartFile file);
}

