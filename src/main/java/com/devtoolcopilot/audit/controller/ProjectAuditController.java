package com.devtoolcopilot.audit.controller;

import com.devtoolcopilot.audit.dto.ProjectAuditExportResponse;
import com.devtoolcopilot.audit.dto.ProjectAuditListResponse;
import com.devtoolcopilot.audit.service.ProjectAuditService;
import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.project.entity.ProjectMemberRole;
import com.devtoolcopilot.project.service.ProjectCollabService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project")
public class ProjectAuditController {
    private final ProjectAuditService projectAuditService;
    private final ProjectCollabService projectCollabService;

    public ProjectAuditController(ProjectAuditService projectAuditService, ProjectCollabService projectCollabService) {
        this.projectAuditService = projectAuditService;
        this.projectCollabService = projectCollabService;
    }

    @GetMapping("/{projectId}/audits")
    public R<ProjectAuditListResponse> list(@PathVariable Long projectId,
                                           @RequestParam(value = "cursor", required = false) Long cursor,
                                           @RequestParam(value = "limit", required = false) Integer limit,
                                           @RequestParam(value = "action", required = false) String action,
                                           @RequestParam(value = "actorUserId", required = false) Long actorUserId,
                                           @RequestParam(value = "q", required = false) String q,
                                           @RequestParam(value = "fromTime", required = false) Long fromTime,
                                           @RequestParam(value = "toTime", required = false) Long toTime) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        projectCollabService.requireAtLeast(me, projectId, ProjectMemberRole.OWNER);
        return R.ok(projectAuditService.list(me, projectId, cursor, limit, action, actorUserId, q, fromTime, toTime));
    }

    @GetMapping("/{projectId}/audits/export")
    public R<ProjectAuditExportResponse> export(@PathVariable Long projectId,
                                               @RequestParam(value = "action", required = false) String action,
                                               @RequestParam(value = "actorUserId", required = false) Long actorUserId,
                                               @RequestParam(value = "q", required = false) String q,
                                               @RequestParam(value = "fromTime", required = false) Long fromTime,
                                               @RequestParam(value = "toTime", required = false) Long toTime) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        projectCollabService.requireAtLeast(me, projectId, ProjectMemberRole.OWNER);
        return R.ok(projectAuditService.exportCsv(me, projectId, action, actorUserId, q, fromTime, toTime));
    }

    @DeleteMapping("/{projectId}/audits/{id}")
    public R<Void> deleteOne(@PathVariable Long projectId, @PathVariable Long id) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        projectCollabService.requireAtLeast(me, projectId, ProjectMemberRole.OWNER);
        projectAuditService.deleteOne(me, projectId, id);
        return R.ok();
    }

    @DeleteMapping("/{projectId}/audits")
    public R<Integer> clear(@PathVariable Long projectId,
                            @RequestParam(value = "action", required = false) String action,
                            @RequestParam(value = "actorUserId", required = false) Long actorUserId,
                            @RequestParam(value = "q", required = false) String q,
                            @RequestParam(value = "fromTime", required = false) Long fromTime,
                            @RequestParam(value = "toTime", required = false) Long toTime) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        projectCollabService.requireAtLeast(me, projectId, ProjectMemberRole.OWNER);
        return R.ok(projectAuditService.clear(me, projectId, action, actorUserId, q, fromTime, toTime));
    }
}
