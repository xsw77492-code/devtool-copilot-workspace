package com.devtoolcopilot.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ChatAttachmentItem {
    private Long id;
    private String originalName;
    private String contentType;
    private String fileType;
    private Long sizeBytes;
    private LocalDateTime createTime;
}
