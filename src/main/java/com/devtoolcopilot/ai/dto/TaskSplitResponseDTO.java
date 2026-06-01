package com.devtoolcopilot.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TaskSplitResponseDTO {
    private List<TaskPlanDTO> plans;
}
