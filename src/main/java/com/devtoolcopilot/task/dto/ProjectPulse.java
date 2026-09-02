package com.devtoolcopilot.task.dto;

import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.timeline.entity.TaskTimeline;

import java.util.List;

/**
 * 单个项目的脉冲数据（任务 + 生命周期事件），用于生命周期中心聚合。
 */
public class ProjectPulse {
    private Long projectId;
    private List<Task> tasks;
    private List<TaskTimeline> events;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }

    public List<TaskTimeline> getEvents() {
        return events;
    }

    public void setEvents(List<TaskTimeline> events) {
        this.events = events;
    }
}
