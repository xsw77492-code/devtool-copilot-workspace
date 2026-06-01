package com.devtoolcopilot.task.timeline.service;

import com.devtoolcopilot.task.timeline.entity.TaskTimeline;
import com.devtoolcopilot.task.timeline.entity.TaskTimelineType;

import java.util.List;

public interface TaskTimelineService {
    Long addEvent(Long userId, Long projectId, Long taskId, TaskTimelineType type, String title, String detail);

    List<TaskTimeline> listByTask(Long userId, Long projectId, Long taskId);
}
