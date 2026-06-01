package com.devtoolcopilot.task.deliverable.dto;

import lombok.Data;

@Data
public class TaskDeliverableCreateRequest {
    private String type;
    private String title;
    private String url;
    private String content;
}

