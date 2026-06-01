package com.devtoolcopilot.task.template.service;

import com.devtoolcopilot.task.template.entity.TaskTemplate;

import java.util.List;

public interface TaskTemplateService {
    List<TaskTemplate> list(Long userId, Long projectId);

    TaskTemplate create(Long userId, Long projectId, String name, String payloadJson);

    TaskTemplate update(Long userId, Long id, String name, String payloadJson);

    void delete(Long userId, Long id);
}

