package com.devtoolcopilot.task.search.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskSearchItem {
    private Long id;
    private Long projectId;
    private Integer projectArchived;
    private String projectName;
    private String title;
    private String status;
    private String priority;
    private String tags;
    private String assignee;
    private Long assigneeId;
    private LocalDateTime dueTime;
    private Long milestoneId;
    private String milestoneName;
    private LocalDateTime updatedAt;
    private LocalDateTime createTime;
}

