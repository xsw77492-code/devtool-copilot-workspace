package com.devtoolcopilot.project.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GroupTaskCreateRequest {
    private String title;
    private String description;
    /** HIGH / MEDIUM / LOW，默认 MEDIUM */
    private String priority;
    private Long assigneeId;
    private LocalDateTime dueTime;
}
