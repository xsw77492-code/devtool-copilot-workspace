package com.devtoolcopilot.task.deliverable.dto;

import lombok.Data;

@Data
public class TaskDeliverableUpdateRequest {
    private String title;
    private String url;
    private String content;
    private String status;
    private Long sort;
}

