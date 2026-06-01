package com.devtoolcopilot.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class DashboardTaskActionItem {
    private Long taskId;
    private Long projectId;
    private String projectName;
    private String title;
    private String status;
    private String priority;
    private String assigneeName;
    private LocalDateTime dueTime;
    private Long commentCount;
}
