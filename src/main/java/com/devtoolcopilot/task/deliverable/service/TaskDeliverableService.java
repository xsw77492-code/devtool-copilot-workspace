package com.devtoolcopilot.task.deliverable.service;

import com.devtoolcopilot.task.deliverable.dto.TaskDeliverableItem;

import java.util.List;

public interface TaskDeliverableService {
    List<TaskDeliverableItem> listByTask(Long userId, Long taskId);

    Long create(Long userId, Long taskId, String type, String title, String url, String content);

    boolean update(Long userId, Long deliverableId, String title, String url, String content, String status, Long sort);

    boolean move(Long userId, Long deliverableId, Long beforeId, Long afterId);

    boolean delete(Long userId, Long deliverableId);
}
