package com.devtoolcopilot.task.checklist.service;

import com.devtoolcopilot.task.checklist.dto.TaskChecklistItemDTO;

import java.util.List;

public interface TaskChecklistService {
    List<TaskChecklistItemDTO> listByTask(Long userId, Long taskId);

    Long create(Long userId, Long taskId, String content);

    boolean update(Long userId, Long id, String content, Boolean done);

    boolean delete(Long userId, Long id);
}

