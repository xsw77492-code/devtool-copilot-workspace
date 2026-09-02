package com.devtoolcopilot.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ChatFileItem {
    private Long attachmentId;
    private String originalName;
    private String contentType;
    private String fileType;
    private Long sizeBytes;
    private Long senderId;
    private String senderName;
    private LocalDateTime createTime;
}
