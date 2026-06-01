package com.devtoolcopilot.task.comment.dto;

import lombok.Data;

@Data
public class TaskCommentCreateRequest {
    private String content;
    private Long replyToId;
}

