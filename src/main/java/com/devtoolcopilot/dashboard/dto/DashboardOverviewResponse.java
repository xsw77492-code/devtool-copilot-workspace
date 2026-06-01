package com.devtoolcopilot.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class DashboardOverviewResponse {
    private Long projectTotal;
    private Long taskTotal;
    private Long doneTaskTotal;
    private Double taskDoneRate;
    private Long aiCallTotal;
    private Long tasksCreatedThisWeek;

    private List<DashboardDayCountItem> taskTrend7d;
    private List<DashboardDayCountItem> aiTrend7d;
    private List<DashboardStatusCountItem> taskStatusDist;
    private List<DashboardMemberActivityItem> memberActivity7d;

    private List<DashboardTaskActionItem> myActions;
    private List<DashboardTaskActionItem> riskTasks;
    private List<DashboardTaskActionItem> topDiscussedTasks;

    private Long wipTotal;
    private List<DashboardDayCountItem> throughputTrend;
    private DashboardCycleTimeStats cycleTime;
}
