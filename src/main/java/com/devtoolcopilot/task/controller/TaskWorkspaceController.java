package com.devtoolcopilot.task.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.dashboard.dto.DashboardOverviewResponse;
import com.devtoolcopilot.dashboard.service.DashboardService;
import com.devtoolcopilot.project.dto.ProjectMembersResponse;
import com.devtoolcopilot.project.entity.Project;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.project.service.ProjectService;
import com.devtoolcopilot.task.dto.WorkspaceMyWorkItem;
import com.devtoolcopilot.task.dto.WorkspaceExportRequest;
import com.devtoolcopilot.task.dto.WorkspaceExportResponse;
import com.devtoolcopilot.task.dto.WorkspaceWeeklyReportResponse;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.entity.TaskStatus;
import com.devtoolcopilot.task.service.TaskService;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/task/workspace", "/api/tasks/workspace"})
public class TaskWorkspaceController {
    private final TaskService taskService;
    private final ProjectCollabService projectCollabService;
    private final DashboardService dashboardService;
    private final ProjectService projectService;

    public TaskWorkspaceController(TaskService taskService,
                                   ProjectCollabService projectCollabService,
                                   DashboardService dashboardService,
                                   ProjectService projectService) {
        this.taskService = taskService;
        this.projectCollabService = projectCollabService;
        this.dashboardService = dashboardService;
        this.projectService = projectService;
    }

    @GetMapping({"/my-work", "/my-work/"})
    public R<List<WorkspaceMyWorkItem>> myWork(@RequestParam(required = false) Integer limit) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        int lim = limit == null ? 6 : Math.max(1, Math.min(limit, 20));
        List<Project> projects = projectService.listByUserId(userId, false);
        if (projects == null || projects.isEmpty()) return R.ok(List.of());
        List<Long> projectIds = projects.stream().map(Project::getId).filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, String> nameMap = new HashMap<>();
        projects.forEach(p -> nameMap.put(p.getId(), p.getName()));

        List<Task> tasks = taskService.myWork(userId, projectIds, lim);
        List<WorkspaceMyWorkItem> out = new ArrayList<>();
        for (Task t : tasks) {
            WorkspaceMyWorkItem it = new WorkspaceMyWorkItem();
            it.setTaskId(t.getId());
            it.setProjectId(t.getProjectId());
            it.setProjectName(nameMap.getOrDefault(t.getProjectId(), ""));
            it.setTitle(t.getTitle());
            it.setStatus(t.getStatus());
            it.setPriority(t.getPriority());
            it.setDueTime(t.getDueTime());
            it.setUpdatedAt(t.getUpdatedAt());
            out.add(it);
        }
        return R.ok(out);
    }

    @PostMapping({"/export", "/export/"})
    public R<WorkspaceExportResponse> export(@RequestBody WorkspaceExportRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        Long projectId = req == null ? null : req.getProjectId();
        if (projectId == null) return R.fail(400, "projectId不能为空");
        try {
            projectCollabService.requireMember(userId, projectId);
            ProjectMembersResponse members = projectCollabService.members(userId, projectId);
            Map<Long, String> userMap = new HashMap<>();
            if (members != null && members.getMembers() != null) {
                members.getMembers().forEach(m -> userMap.put(m.getUserId(), m.getUsername()));
            }

            List<Task> list = filteredTasks(userId, projectId, req, userMap);
            String csv = buildCsv(list, userMap);

            WorkspaceExportResponse resp = new WorkspaceExportResponse();
            resp.setFilename("tasks_project_" + projectId + ".csv");
            resp.setContent(csv);

            projectCollabService.addActivity(projectId, userId, "WORKSPACE_EXPORT_CSV",
                    "{\"mode\":\"" + safe(req.getMode()) + "\",\"q\":\"" + safe(req.getQ()) + "\",\"overdue\":" + (Boolean.TRUE.equals(req.getOverdueOnly()) ? 1 : 0) + "}");
            return R.ok(resp);
        } catch (IllegalArgumentException e) {
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) return R.fail(403, "无权限或项目不存在");
            return R.fail(400, "导出失败");
        }
    }

    @GetMapping({"/weekly-report", "/weekly-report/"})
    public R<WorkspaceWeeklyReportResponse> weeklyReport(@RequestParam Long projectId,
                                                        @RequestParam(required = false) String startDate,
                                                        @RequestParam(required = false) String endDate) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        if (projectId == null) return R.fail(400, "projectId不能为空");
        try {
            projectCollabService.requireMember(userId, projectId);
            ProjectMembersResponse members = projectCollabService.members(userId, projectId);
            Map<Long, String> userMap = new HashMap<>();
            if (members != null && members.getMembers() != null) {
                members.getMembers().forEach(m -> userMap.put(m.getUserId(), m.getUsername()));
            }

            LocalDateRange range = resolveRange(startDate, endDate);
            DashboardOverviewResponse ov = dashboardService.overview(userId, projectId, range.start.toString(), range.end.toString(), false);
            String md = buildWeeklyMarkdown(userId, projectId, ov, userMap);

            WorkspaceWeeklyReportResponse resp = new WorkspaceWeeklyReportResponse();
            resp.setTitle("周报 · 项目 " + projectId);
            resp.setContent(md);

            projectCollabService.addActivity(projectId, userId, "WORKSPACE_WEEKLY_REPORT",
                    "{\"start\":\"" + range.start + "\",\"end\":\"" + range.end + "\"}");
            return R.ok(resp);
        } catch (IllegalArgumentException e) {
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) return R.fail(403, "无权限或项目不存在");
            return R.fail(400, "生成失败");
        }
    }

    private List<Task> filteredTasks(Long userId, Long projectId, WorkspaceExportRequest req, Map<Long, String> userMap) {
        List<Task> list = taskService.listByProjectId(userId, projectId);
        String mode = safe(req == null ? null : req.getMode());
        String q = safe(req == null ? null : req.getQ()).trim().toLowerCase();
        boolean overdueOnly = req != null && Boolean.TRUE.equals(req.getOverdueOnly());
        String sort = safe(req == null ? null : req.getSort());

        Set<Long> participated = null;
        if ("participated".equalsIgnoreCase(mode)) {
            List<Long> ids = taskService.participatedTaskIds(userId, projectId);
            participated = new HashSet<>(ids == null ? List.of() : ids);
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());

        List<Task> out = new ArrayList<>();
        for (Task t : list) {
            if (t == null) continue;
            if ("mine".equalsIgnoreCase(mode)) {
                if (t.getAssigneeId() == null || !t.getAssigneeId().equals(userId)) continue;
            } else if ("unassigned".equalsIgnoreCase(mode)) {
                if (t.getAssigneeId() != null && t.getAssigneeId() != 0) continue;
            } else if ("participated".equalsIgnoreCase(mode)) {
                boolean mine = t.getAssigneeId() != null && t.getAssigneeId().equals(userId);
                boolean in = participated != null && participated.contains(t.getId());
                if (!mine && !in) continue;
            }
            if (!q.isEmpty()) {
                String title = t.getTitle() == null ? "" : t.getTitle().toLowerCase();
                if (!title.contains(q)) continue;
            }
            if (overdueOnly) {
                if (t.getStatus() == TaskStatus.DONE) continue;
                if (t.getDueTime() == null) continue;
                if (!t.getDueTime().isBefore(now)) continue;
            }
            out.add(t);
        }

        if ("due".equalsIgnoreCase(sort)) {
            out.sort((a, b) -> {
                LocalDateTime ad = a.getDueTime();
                LocalDateTime bd = b.getDueTime();
                if (ad == null && bd == null) return Long.compare(b.getId(), a.getId());
                if (ad == null) return 1;
                if (bd == null) return -1;
                int c = ad.compareTo(bd);
                if (c != 0) return c;
                return Long.compare(b.getId(), a.getId());
            });
        } else if ("priority".equalsIgnoreCase(sort)) {
            out.sort((a, b) -> {
                int ar = priRank(a.getPriority());
                int br = priRank(b.getPriority());
                if (ar != br) return Integer.compare(br, ar);
                return Long.compare(b.getId(), a.getId());
            });
        } else {
            out.sort(Comparator.comparing(Task::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        }
        return out;
    }

    private String buildCsv(List<Task> list, Map<Long, String> userMap) {
        DateTimeFormatter dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder sb = new StringBuilder();
        sb.append(csv("id")).append(',')
                .append(csv("title")).append(',')
                .append(csv("status")).append(',')
                .append(csv("priority")).append(',')
                .append(csv("assignee")).append(',')
                .append(csv("dueTime")).append(',')
                .append(csv("updatedAt")).append('\n');
        for (Task t : list) {
            String assignee = "";
            if (t.getAssigneeId() != null) {
                assignee = userMap.getOrDefault(t.getAssigneeId(), "#" + t.getAssigneeId());
            }
            String due = t.getDueTime() == null ? "" : dt.format(t.getDueTime());
            String upd = t.getUpdatedAt() == null ? "" : dt.format(t.getUpdatedAt());
            sb.append(csv(t.getId())).append(',')
                    .append(csv(t.getTitle())).append(',')
                    .append(csv(t.getStatus() == null ? "" : t.getStatus().name())).append(',')
                    .append(csv(t.getPriority() == null ? "" : t.getPriority().toUpperCase())).append(',')
                    .append(csv(assignee)).append(',')
                    .append(csv(due)).append(',')
                    .append(csv(upd)).append('\n');
        }
        return sb.toString();
    }

    private String buildWeeklyMarkdown(Long userId,
                                       Long projectId,
                                       DashboardOverviewResponse ov,
                                       Map<Long, String> userMap) {
        DateTimeFormatter dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        long newCnt = ov == null || ov.getTasksCreatedThisWeek() == null ? 0L : ov.getTasksCreatedThisWeek();
        long doneCnt = 0L;
        if (ov != null && ov.getThroughputTrend() != null) {
            doneCnt = ov.getThroughputTrend().stream()
                    .filter(Objects::nonNull)
                    .map(x -> x.getCount())
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .sum();
        }
        int overdue = ov == null || ov.getRiskTasks() == null ? 0 : ov.getRiskTasks().size();

        StringBuilder sb = new StringBuilder();
        sb.append("# 周报 · 项目 ").append(projectId).append("\n\n");
        sb.append("本周新增 **").append(newCnt).append("**，完成 **").append(doneCnt).append("**，逾期 **").append(overdue).append("**。").append("\n\n");

        sb.append("## 逾期风险（Top）").append("\n");
        if (ov == null || ov.getRiskTasks() == null || ov.getRiskTasks().isEmpty()) {
            sb.append("- 无").append("\n");
        } else {
            ov.getRiskTasks().stream().limit(8).forEach(x -> {
                String due = x.getDueTime() == null ? "-" : dt.format(x.getDueTime());
                sb.append("- [ ] ").append(x.getTitle()).append("（due: ").append(due).append("）").append("\n");
            });
        }

        sb.append("\n## 我负责未完成（Top）").append("\n");
        List<Task> mine = taskService.listByProjectId(userId, projectId).stream()
                .filter(t -> t != null && t.getAssigneeId() != null && t.getAssigneeId().equals(userId) && t.getStatus() != TaskStatus.DONE)
                .sorted((a, b) -> {
                    LocalDateTime ad = a.getDueTime();
                    LocalDateTime bd = b.getDueTime();
                    if (ad == null && bd == null) return Long.compare(b.getId(), a.getId());
                    if (ad == null) return 1;
                    if (bd == null) return -1;
                    int c = ad.compareTo(bd);
                    if (c != 0) return c;
                    return Long.compare(b.getId(), a.getId());
                })
                .limit(10)
                .toList();
        if (mine.isEmpty()) {
            sb.append("- 无").append("\n");
        } else {
            for (Task t : mine) {
                String due = t.getDueTime() == null ? "" : (" · due: " + dt.format(t.getDueTime()));
                sb.append("- [ ] ").append(t.getTitle()).append("（").append(t.getStatus().name()).append(due).append("）").append("\n");
            }
        }

        return sb.toString();
    }

    private int priRank(String p) {
        String v = safe(p).toUpperCase();
        if ("HIGH".equals(v)) return 3;
        if ("MEDIUM".equals(v)) return 2;
        if ("LOW".equals(v)) return 1;
        return 0;
    }

    private String csv(Object raw) {
        String s = raw == null ? "" : String.valueOf(raw);
        s = s.replace("\"", "\"\"");
        return "\"" + s + "\"";
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private LocalDateRange resolveRange(String startDate, String endDate) {
        LocalDate now = LocalDate.now(ZoneId.systemDefault());
        LocalDate start = null;
        LocalDate end = null;
        try {
            if (startDate != null && !startDate.isBlank()) start = LocalDate.parse(startDate.trim());
        } catch (Exception ignored) {
        }
        try {
            if (endDate != null && !endDate.isBlank()) end = LocalDate.parse(endDate.trim());
        } catch (Exception ignored) {
        }
        if (start == null || end == null) {
            LocalDate monday = now.with(DayOfWeek.MONDAY);
            start = monday;
            end = monday.plusDays(6);
        }
        if (start.isAfter(end)) {
            LocalDate t = start;
            start = end;
            end = t;
        }
        return new LocalDateRange(start, end);
    }

    private record LocalDateRange(LocalDate start, LocalDate end) {
    }
}
