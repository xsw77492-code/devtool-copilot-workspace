package com.devtoolcopilot.task.view.dto;

import lombok.Data;

@Data
public class TaskBoardViewUpsertRequest {
    private Long projectId;
    private String name;
    private String color;
    private String filtersJson;
}
