package com.devtoolcopilot.task.dto;

import com.devtoolcopilot.task.entity.TaskStatus;
import lombok.Data;

@Data
public class TaskKanbanMoveRequest {
    private Long projectId;
    private Long taskId;
    private TaskStatus toStatus;
    private Long beforeId;
    private Long afterId;
    private String baseUpdatedAt;
    private Boolean forceDone;
}
