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
import com.devtoolcopilot.project.dto.ProjectTeamCenterResponse;
import com.devtoolcopilot.project.dto.TeamGroupCreateRequest;
import com.devtoolcopilot.project.dto.TeamGroupInviteCreateRequest;
import com.devtoolcopilot.project.dto.TeamGroupInviteCreateResponse;
import com.devtoolcopilot.project.dto.TeamGroupUpdateRequest;
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

    @GetMapping("/team/center")
    public R<ProjectTeamCenterResponse> teamCenter(@RequestParam(value = "groupId", required = false) Long groupId) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        return R.ok(projectCollabService.teamCenter(me, groupId));
    }

    @PostMapping("/team/groups")
    public R<Long> createTeamGroup(@Valid @RequestBody TeamGroupCreateRequest req) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "UNAUTHENTICATED");
        return R.ok(projectCollabService.createTeamGroup(me, req.getName()));
    }

    @PutMapping("/team/groups/{groupId}")
    public R<Void> renameTeamGroup(@PathVariable Long groupId, @Valid @RequestBody TeamGroupUpdateRequest req) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "UNAUTHENTICATED");
        projectCollabService.renameTeamGroup(me, groupId, req.getName());
        return R.ok();
    }

    @DeleteMapping("/team/groups/{groupId}")
    public R<Void> deleteTeamGroup(@PathVariable Long groupId) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "UNAUTHENTICATED");
        projectCollabService.deleteTeamGroup(me, groupId);
        return R.ok();
    }

    @PostMapping("/team/groups/{groupId}/invites")
    public R<TeamGroupInviteCreateResponse> inviteToTeamGroup(@PathVariable Long groupId,
                                                               @Valid @RequestBody TeamGroupInviteCreateRequest req) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "UNAUTHENTICATED");
        return R.ok(projectCollabService.inviteToTeamGroup(me, groupId, req.getEmail()));
    }

    @PostMapping("/team/invites/accept")
    public R<Long> acceptTeamGroupInvite(@Valid @RequestBody ProjectInviteHandleRequest req) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "UNAUTHENTICATED");
        return R.ok(projectCollabService.acceptTeamGroupInvite(me, req.getToken()));
    }

    @PostMapping("/team/invites/reject")
    public R<Long> rejectTeamGroupInvite(@Valid @RequestBody ProjectInviteHandleRequest req) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "UNAUTHENTICATED");
        return R.ok(projectCollabService.rejectTeamGroupInvite(me, req.getToken()));
    }

    @DeleteMapping("/team/groups/{groupId}/invites/{inviteId}")
    public R<Void> cancelTeamGroupInvite(@PathVariable Long groupId, @PathVariable Long inviteId) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "UNAUTHENTICATED");
        projectCollabService.cancelTeamGroupInvite(me, groupId, inviteId);
        return R.ok();
    }

    @PostMapping("/team/groups/{groupId}/invites/{inviteId}/reissue")
    public R<TeamGroupInviteCreateResponse> reissueTeamGroupInvite(@PathVariable Long groupId, @PathVariable Long inviteId) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "UNAUTHENTICATED");
        return R.ok(projectCollabService.reissueTeamGroupInvite(me, groupId, inviteId));
    }

    @DeleteMapping("/team/groups/{groupId}/members/{userId}")
    public R<Void> removeTeamGroupMember(@PathVariable Long groupId, @PathVariable Long userId) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "UNAUTHENTICATED");
        projectCollabService.removeTeamGroupMember(me, groupId, userId);
        return R.ok();
    }

    @DeleteMapping("/team/groups/{groupId}/members/me")
    public R<Void> leaveTeamGroup(@PathVariable Long groupId) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "UNAUTHENTICATED");
        projectCollabService.leaveTeamGroup(me, groupId);
        return R.ok();
    }

    @PostMapping("/team/groups/{groupId}/members/{userId}/transfer-owner")
    public R<Void> transferTeamGroupOwner(@PathVariable Long groupId, @PathVariable Long userId) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "UNAUTHENTICATED");
        projectCollabService.transferTeamGroupOwnership(me, groupId, userId);
        return R.ok();
    }

    @GetMapping("/team/invites/mine")
    public R<List<ProjectTeamCenterResponse.InviteItem>> teamInvitesMine() {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "UNAUTHENTICATED");
        return R.ok(projectCollabService.teamInvitesMine(me));
    }

    @PostMapping("/team/invites/{inviteId}/accept-by-id")
    public R<Long> acceptTeamGroupInviteById(@PathVariable Long inviteId) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "UNAUTHENTICATED");
        return R.ok(projectCollabService.acceptTeamGroupInviteById(me, inviteId));
    }

    @PostMapping("/team/invites/{inviteId}/reject-by-id")
    public R<Long> rejectTeamGroupInviteById(@PathVariable Long inviteId) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "UNAUTHENTICATED");
        return R.ok(projectCollabService.rejectTeamGroupInviteById(me, inviteId));
    }

    @DeleteMapping("/team/groups/{groupId}/activities/{activityId}")
    public R<Void> deleteTeamGroupActivity(@PathVariable Long groupId, @PathVariable Long activityId) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "UNAUTHENTICATED");
        projectCollabService.deleteTeamGroupActivity(me, groupId, activityId);
        return R.ok();
    }

    @DeleteMapping("/team/groups/{groupId}/activities")
    public R<Integer> clearTeamGroupActivities(@PathVariable Long groupId) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "UNAUTHENTICATED");
        return R.ok(projectCollabService.clearTeamGroupActivities(me, groupId));
    }
}
