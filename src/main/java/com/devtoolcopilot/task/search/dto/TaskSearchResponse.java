package com.devtoolcopilot.task.search.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TaskSearchResponse {
    private Long total;
    private List<TaskSearchItem> items;
}

