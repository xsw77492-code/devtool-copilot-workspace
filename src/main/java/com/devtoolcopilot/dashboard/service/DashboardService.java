package com.devtoolcopilot.dashboard.service;

import com.devtoolcopilot.dashboard.dto.DashboardOverviewResponse;
import com.devtoolcopilot.dashboard.dto.DashboardDoneTaskItem;

import java.util.List;

public interface DashboardService {
    DashboardOverviewResponse overview(Long userId, Long projectId, String startDate, String endDate, boolean lite);

    List<DashboardDoneTaskItem> doneTasksByDay(Long userId, Long projectId, String day);
}
