package com.devtoolcopilot.project.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.project.dto.ProjectActivityItem;
import com.devtoolcopilot.project.dto.ProjectInviteCreateRequest;
import com.devtoolcopilot.project.dto.ProjectInviteCreateResponse;
import com.devtoolcopilot.project.dto.ProjectInviteHandleRequest;
import com.devtoolcopilot.project.dto.ProjectInviteItem;
import com.devtoolcopilot.project.dto.ProjectMemberDisabledRequest;
import com.devtoolcopilot.project.dto.ProjectMemberRoleUpdateRequest;
import com.devtoolcopilot.project.dto.ProjectMembersExportResponse;
import com.devtoolcopilot.project.dto.ProjectMembersResponse;
import com.devtoolcopilot.project.service.ProjectCollabService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/project")
public class ProjectCollabController {
    private final ProjectCollabService projectCollabService;

    public ProjectCollabController(ProjectCollabService projectCollabService) {
        this.projectCollabService = projectCollabService;
    }

    @GetMapping("/{projectId}/members")
    public R<ProjectMembersResponse> members(@PathVariable Long projectId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(projectCollabService.members(userId, projectId));
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    public R<Void> removeMember(@PathVariable Long projectId, @PathVariable Long userId) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        projectCollabService.removeMember(me, projectId, userId);
        return R.ok();
    }

    @PostMapping("/{projectId}/invites")
    public R<ProjectInviteCreateResponse> invite(@PathVariable Long projectId, @Valid @RequestBody ProjectInviteCreateRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(projectCollabService.invite(userId, projectId, req.getEmail(), req.getRole()));
    }

    @GetMapping("/{projectId}/invites")
    public R<List<ProjectInviteItem>> invites(@PathVariable Long projectId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(projectCollabService.invites(userId, projectId));
    }

    @DeleteMapping("/{projectId}/invites/{inviteId}")
    public R<Void> cancelInvite(@PathVariable Long projectId, @PathVariable Long inviteId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        projectCollabService.cancelInvite(userId, projectId, inviteId);
        return R.ok();
    }

    @PostMapping("/{projectId}/invites/{inviteId}/reissue")
    public R<ProjectInviteCreateResponse> reissue(@PathVariable Long projectId, @PathVariable Long inviteId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(projectCollabService.reissueInvite(userId, projectId, inviteId));
    }

    @PostMapping("/invites/accept")
    public R<Long> accept(@Valid @RequestBody ProjectInviteHandleRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(projectCollabService.acceptInvite(userId, req.getToken()));
    }

    @PostMapping("/invites/reject")
    public R<Long> reject(@Valid @RequestBody ProjectInviteHandleRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(projectCollabService.rejectInvite(userId, req.getToken()));
    }

    @GetMapping("/{projectId}/activities")
    public R<List<ProjectActivityItem>> activities(@PathVariable Long projectId,
                                                   @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(projectCollabService.activities(userId, projectId, limit));
    }

    @DeleteMapping("/{projectId}/activities/{activityId}")
    public R<Void> deleteActivity(@PathVariable Long projectId, @PathVariable Long activityId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        projectCollabService.deleteActivity(userId, projectId, activityId);
        return R.ok();
    }

    @DeleteMapping("/{projectId}/activities")
    public R<Integer> clearActivities(@PathVariable Long projectId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(projectCollabService.clearActivities(userId, projectId));
    }

    @PutMapping("/{projectId}/members/{userId}/role")
    public R<Void> updateRole(@PathVariable Long projectId,
                              @PathVariable Long userId,
                              @Valid @RequestBody ProjectMemberRoleUpdateRequest req) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        projectCollabService.updateMemberRole(me, projectId, userId, req.getRole());
        return R.ok();
    }

    @PutMapping("/{projectId}/members/{userId}/disabled")
    public R<Void> setDisabled(@PathVariable Long projectId,
                               @PathVariable Long userId,
                               @Valid @RequestBody ProjectMemberDisabledRequest req) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        projectCollabService.setMemberDisabled(me, projectId, userId, Boolean.TRUE.equals(req.getDisabled()));
        return R.ok();
    }

    @PostMapping("/{projectId}/members/{userId}/transfer-owner")
    public R<Void> transferOwner(@PathVariable Long projectId, @PathVariable Long userId) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        projectCollabService.transferOwnership(me, projectId, userId);
        return R.ok();
    }

    @DeleteMapping("/{projectId}/members/me")
    public R<Void> leave(@PathVariable Long projectId) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        projectCollabService.leaveProject(me, projectId);
        return R.ok();
    }

    @GetMapping("/{projectId}/members/export")
    public R<ProjectMembersExportResponse> export(@PathVariable Long projectId) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        return R.ok(projectCollabService.exportMembers(me, projectId));
    }
}
