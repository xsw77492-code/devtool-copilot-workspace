package com.devtoolcopilot.attachment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class AttachmentItem {
    private Long id;
    private Long projectId;
    private Long taskId;
    private Long commentId;
    private Long userId;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private LocalDateTime createTime;
}

