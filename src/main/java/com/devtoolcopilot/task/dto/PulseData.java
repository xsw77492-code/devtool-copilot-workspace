package com.devtoolcopilot.task.dto;

import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.timeline.entity.TaskTimeline;
import lombok.Data;

import java.util.List;

@Data
public class PulseData {
    private List<Task> tasks;
    private List<TaskTimeline> events;
}
