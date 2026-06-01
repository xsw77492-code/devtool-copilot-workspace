package com.devtoolcopilot.task.timeline.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.task.timeline.entity.TaskTimeline;
import com.devtoolcopilot.task.timeline.entity.TaskTimelineType;
import com.devtoolcopilot.task.timeline.mapper.TaskTimelineMapper;
import com.devtoolcopilot.task.timeline.service.TaskTimelineService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskTimelineServiceImpl implements TaskTimelineService {
    private final TaskTimelineMapper mapper;

    public TaskTimelineServiceImpl(TaskTimelineMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long addEvent(Long userId, Long projectId, Long taskId, TaskTimelineType type, String title, String detail) {
        if (userId == null || projectId == null || taskId == null) return null;
        TaskTimeline e = new TaskTimeline();
        e.setUserId(userId);
        e.setProjectId(projectId);
        e.setTaskId(taskId);
        e.setType(type);
        e.setTitle(title);
        e.setDetail(detail);
        mapper.insert(e);
        return e.getId();
    }

    @Override
    public List<TaskTimeline> listByTask(Long userId, Long projectId, Long taskId) {
        return mapper.selectList(Wrappers.<TaskTimeline>lambdaQuery()
                .eq(TaskTimeline::getUserId, userId)
                .eq(TaskTimeline::getProjectId, projectId)
                .eq(TaskTimeline::getTaskId, taskId)
                .orderByDesc(TaskTimeline::getId));
    }
}
