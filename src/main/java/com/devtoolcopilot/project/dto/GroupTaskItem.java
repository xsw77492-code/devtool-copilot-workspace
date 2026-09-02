package com.devtoolcopilot.project.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GroupTaskItem {
    private Long id;
    private Long groupId;
    private String title;
    private String description;
    /** HIGH / MEDIUM / LOW */
    private String priority;
    /** TODO / DOING / DONE */
    private String status;
    private Long assigneeId;
    private String assigneeUsername;
    private Long createdBy;
    private String creatorUsername;
    private LocalDateTime dueTime;
    private LocalDateTime doneTime;
    private boolean overdue;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
