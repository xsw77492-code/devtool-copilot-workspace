package com.devtoolcopilot.task.dto;

import lombok.Data;

@Data
public class TaskCreateRequest {
    private Long projectId;
    private String title;
    private String description;
    private String acceptanceCriteria;
    private String priority;
    private String tags;
    private String assignee;
    private Long assigneeId;
    private Long dueTime;
    private Long milestoneId;
    private Long parentTaskId;
    private String type;
    private String source;
}
