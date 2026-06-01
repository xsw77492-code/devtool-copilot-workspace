package com.devtoolcopilot.task.comment.service;

import com.devtoolcopilot.task.comment.dto.TaskCommentDTO;

import java.util.List;

public interface TaskCommentService {
    List<TaskCommentDTO> listByTask(Long userId, Long taskId);

    Long addComment(Long userId, Long taskId, String content, Long replyToId);
}

