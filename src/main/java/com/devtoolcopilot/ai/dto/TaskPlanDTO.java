package com.devtoolcopilot.ai.dto;

import lombok.Data;

@Data
public class TaskPlanDTO {
    private String title;
    private String description;
    private TaskPriority priority;
    private Integer order;
}
