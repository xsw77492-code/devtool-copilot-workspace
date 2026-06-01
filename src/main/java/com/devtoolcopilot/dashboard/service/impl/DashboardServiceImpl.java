package com.devtoolcopilot.dashboard.service.impl;

import com.devtoolcopilot.dashboard.dto.DashboardDayCountItem;
import com.devtoolcopilot.dashboard.dto.DashboardCycleTimeStats;
import com.devtoolcopilot.dashboard.dto.DashboardDoneTaskItem;
import com.devtoolcopilot.dashboard.dto.DashboardMemberActivityItem;
import com.devtoolcopilot.dashboard.dto.DashboardOverviewResponse;
import com.devtoolcopilot.dashboard.dto.DashboardStatusCountItem;
import com.devtoolcopilot.dashboard.dto.DashboardTaskActionItem;
import com.devtoolcopilot.dashboard.mapper.DashboardMapper;
import com.devtoolcopilot.dashboard.service.DashboardService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardServiceImpl implements DashboardService {
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final DashboardMapper dashboardMapper;

    public DashboardServiceImpl(DashboardMapper dashboardMapper) {
        this.dashboardMapper = dashboardMapper;
    }

    @Override
    public DashboardOverviewResponse overview(Long userId, Long projectId, String startDate, String endDate, boolean lite) {
        LocalDate end = parseDayOrNull(endDate);
        if (end == null) end = LocalDate.now();
        LocalDate start = parseDayOrNull(startDate);
        if (start == null) start = end.minusDays(6);
        if (start.isAfter(end)) {
            LocalDate t = start;
            start = end;
            end = t;
        }
        if (ChronoUnit.DAYS.between(start, end) > 120) {
            start = end.minusDays(120);
        }
        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endExclusive = end.plusDays(1).atStartOfDay();

        Long projectTotal = nvl(dashboardMapper.projectTotal(userId, projectId));
        Long taskTotal = nvl(dashboardMapper.taskTotal(userId, projectId, startAt, endExclusive));
        Long doneTaskTotal = nvl(dashboardMapper.doneTaskTotal(userId, projectId, startAt, endExclusive));
        Double taskDoneRate = taskTotal <= 0 ? 0.0 : (doneTaskTotal * 1.0 / taskTotal);
        Long aiCallTotal = nvl(dashboardMapper.aiCallTotal(userId, projectId, startAt, endExclusive));
        Long tasksCreatedThisWeek = nvl(dashboardMapper.tasksCreatedThisWeek(userId, projectId, startAt, endExclusive));

        if (lite) {
            return new DashboardOverviewResponse(
                    projectTotal,
                    taskTotal,
                    doneTaskTotal,
                    taskDoneRate,
                    aiCallTotal,
                    tasksCreatedThisWeek,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    0L,
                    List.of(),
                    new DashboardCycleTimeStats(0L, 0.0, 0.0, 0.0)
            );
        }

        List<DashboardDayCountItem> rawTaskTrend = dashboardMapper.taskTrend7d(userId, projectId, startAt, endExclusive);
        List<DashboardDayCountItem> rawAiTrend = dashboardMapper.aiTrend7d(userId, projectId, startAt, endExclusive);
        List<DashboardDayCountItem> taskTrend7d = fillRange(rawTaskTrend, start, end);
        List<DashboardDayCountItem> aiTrend7d = fillRange(rawAiTrend, start, end);

        List<DashboardStatusCountItem> taskStatusDist = dashboardMapper.taskStatusDist(userId, projectId, startAt, endExclusive);
        if (taskStatusDist == null) taskStatusDist = List.of();

        List<DashboardMemberActivityItem> memberActivity7d = dashboardMapper.memberActivity7d(userId, projectId, startAt, endExclusive);
        if (memberActivity7d == null) memberActivity7d = List.of();

        List<DashboardTaskActionItem> myActions = dashboardMapper.myActions(userId, projectId, startAt, endExclusive, userId);
        if (myActions == null) myActions = List.of();

        List<DashboardTaskActionItem> riskTasks = dashboardMapper.riskTasks(userId, projectId, startAt, endExclusive, LocalDateTime.now());
        if (riskTasks == null) riskTasks = List.of();

        List<DashboardTaskActionItem> topDiscussedTasks = dashboardMapper.topDiscussedTasks(userId, projectId, startAt, endExclusive);
        if (topDiscussedTasks == null) topDiscussedTasks = List.of();

        Long wipTotal = nvl(dashboardMapper.wipTotal(userId, projectId));
        List<DashboardDayCountItem> rawThroughput = dashboardMapper.throughputTrend(userId, projectId, startAt, endExclusive);
        List<DashboardDayCountItem> throughputTrend = fillRange(rawThroughput, start, end);
        DashboardCycleTimeStats cycleTime = calcCycleTime(dashboardMapper.cycleTimeSecondsSamples(userId, projectId, startAt, endExclusive));

        return new DashboardOverviewResponse(
                projectTotal,
                taskTotal,
                doneTaskTotal,
                taskDoneRate,
                aiCallTotal,
                tasksCreatedThisWeek,
                taskTrend7d,
                aiTrend7d,
                taskStatusDist,
                memberActivity7d,
                myActions,
                riskTasks,
                topDiscussedTasks,
                wipTotal,
                throughputTrend,
                cycleTime
        );
    }

    @Override
    public List<DashboardDoneTaskItem> doneTasksByDay(Long userId, Long projectId, String day) {
        LocalDate d = parseDayOrNull(day);
        if (d == null) d = LocalDate.now();
        LocalDateTime startAt = d.atStartOfDay();
        LocalDateTime endExclusive = d.plusDays(1).atStartOfDay();
        List<DashboardDoneTaskItem> rows = dashboardMapper.doneTasksByDay(userId, projectId, startAt, endExclusive);
        return rows == null ? List.of() : rows;
    }

    private static Long nvl(Long v) {
        return v == null ? 0L : v;
    }

    private static LocalDate parseDayOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim(), DAY_FMT);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<DashboardDayCountItem> fillRange(List<DashboardDayCountItem> raw, LocalDate start, LocalDate end) {
        Map<String, Long> map = new HashMap<>();
        if (raw != null) {
            for (DashboardDayCountItem it : raw) {
                if (it == null || it.getDay() == null) continue;
                map.put(it.getDay(), it.getCount() == null ? 0L : it.getCount());
            }
        }
        List<DashboardDayCountItem> out = new ArrayList<>();
        LocalDate cur = start;
        int guard = 0;
        while (!cur.isAfter(end) && guard++ < 200) {
            String day = cur.format(DAY_FMT);
            out.add(new DashboardDayCountItem(day, map.getOrDefault(day, 0L)));
            cur = cur.plusDays(1);
        }
        return out;
    }

    private static DashboardCycleTimeStats calcCycleTime(List<Long> secondsSamples) {
        if (secondsSamples == null) secondsSamples = List.of();
        List<Long> s = new ArrayList<>();
        for (Long v : secondsSamples) {
            if (v == null) continue;
            if (v <= 0) continue;
            s.add(v);
        }
        if (s.isEmpty()) {
            return new DashboardCycleTimeStats(0L, 0.0, 0.0, 0.0);
        }
        Collections.sort(s);
        int n = s.size();
        long p50 = percentile(s, 0.50);
        long p90 = percentile(s, 0.90);
        double avg = s.stream().mapToLong(x -> x).average().orElse(0.0);
        return new DashboardCycleTimeStats(
                (long) n,
                round2(p50 / 86400.0),
                round2(p90 / 86400.0),
                round2(avg / 86400.0)
        );
    }

    private static long percentile(List<Long> sorted, double p) {
        if (sorted == null || sorted.isEmpty()) return 0L;
        int n = sorted.size();
        int idx = (int) Math.ceil(p * n) - 1;
        if (idx < 0) idx = 0;
        if (idx >= n) idx = n - 1;
        return sorted.get(idx);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
