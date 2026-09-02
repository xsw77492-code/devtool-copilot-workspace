package com.devtoolcopilot.project.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GroupTaskUpdateRequest {
    private String title;
    private String description;
    private String priority;
    private LocalDateTime dueTime;
}
