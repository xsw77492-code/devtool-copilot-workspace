package com.devtoolcopilot.ai.file.controller;

import com.devtoolcopilot.ai.file.dto.AiFileUploadResponse;
import com.devtoolcopilot.ai.file.service.AiFileService;
import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.common.exception.ApiException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai/file")
public class AiFileController {
    private final AiFileService aiFileService;

    public AiFileController(AiFileService aiFileService) {
        this.aiFileService = aiFileService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<AiFileUploadResponse> upload(@RequestParam Long projectId, @RequestPart("file") MultipartFile file) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new ApiException(401, "未登录");
        return R.ok(aiFileService.uploadAndExtract(userId, projectId, file));
    }
}

