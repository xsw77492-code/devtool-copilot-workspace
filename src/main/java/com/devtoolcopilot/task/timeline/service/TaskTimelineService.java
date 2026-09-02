package com.devtoolcopilot.task.timeline.service;

import com.devtoolcopilot.task.timeline.entity.TaskTimeline;
import com.devtoolcopilot.task.timeline.entity.TaskTimelineType;

import java.util.List;

public interface TaskTimelineService {
    Long addEvent(Long userId, Long projectId, Long taskId, TaskTimelineType type, String title, String detail);

    List<TaskTimeline> listByTask(Long userId, Long projectId, Long taskId);

    /** 项目级视角：按项目查询全部任务的状态事件（创建 + 状态变更），不按用户过滤 */
    List<TaskTimeline> listStatusEventsByProject(Long projectId);

    /** 批量：一次查询多个项目的全部状态事件，用于生命周期中心聚合 */
    List<TaskTimeline> listStatusEventsByProjectIds(List<Long> projectIds);
}
