package com.devtoolcopilot.dashboard.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.dashboard.dto.DashboardDoneTaskItem;
import com.devtoolcopilot.dashboard.dto.DashboardOverviewResponse;
import com.devtoolcopilot.dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping({"/api/dashboard", "/api/dashboard/", "/api/dashboards"})
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping({"/overview", "/overview/"})
    public R<DashboardOverviewResponse> overview(@RequestParam(value = "projectId", required = false) Long projectId,
                                                 @RequestParam(value = "startDate", required = false) String startDate,
                                                 @RequestParam(value = "endDate", required = false) String endDate,
                                                 @RequestParam(value = "lite", required = false, defaultValue = "false") boolean lite) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(dashboardService.overview(userId, projectId, startDate, endDate, lite));
    }

    @GetMapping({"/throughput/tasks", "/throughput/tasks/"})
    public R<List<DashboardDoneTaskItem>> throughputTasks(@RequestParam(value = "projectId", required = false) Long projectId,
                                                         @RequestParam(value = "day") String day) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(dashboardService.doneTasksByDay(userId, projectId, day));
    }
}
