package com.devtoolcopilot.task.deliverable.dto;

import lombok.Data;

@Data
public class TaskDeliverableMoveRequest {
    private Long beforeId;
    private Long afterId;
}

