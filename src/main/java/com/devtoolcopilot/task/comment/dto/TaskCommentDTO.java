package com.devtoolcopilot.task.comment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class TaskCommentDTO {
    private Long id;
    private Long projectId;
    private Long taskId;
    private Long userId;
    private String username;
    private String content;
    private Long replyToId;
    private LocalDateTime createTime;
}

