package com.devtoolcopilot.milestone.dto;

import lombok.Data;

@Data
public class MilestoneCreateRequest {
    private Long projectId;
    private String name;
    private String description;
    private Long dueTime;
}

