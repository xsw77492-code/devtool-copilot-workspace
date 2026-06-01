package com.devtoolcopilot.task.view.service;

import com.devtoolcopilot.task.view.entity.TaskBoardView;

import java.util.List;

public interface TaskBoardViewService {
    List<TaskBoardView> list(Long userId, Long projectId);

    TaskBoardView create(Long userId, Long projectId, String name, String color, String filtersJson);

    TaskBoardView update(Long userId, Long id, String name, String color, String filtersJson);

    void delete(Long userId, Long id);
}
