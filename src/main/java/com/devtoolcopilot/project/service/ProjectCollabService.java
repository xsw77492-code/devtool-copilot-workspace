package com.devtoolcopilot.project.service;

import com.devtoolcopilot.project.dto.ProjectActivityItem;
import com.devtoolcopilot.project.dto.ProjectInviteCreateResponse;
import com.devtoolcopilot.project.dto.ProjectInviteItem;
import com.devtoolcopilot.project.dto.ProjectMembersExportResponse;
import com.devtoolcopilot.project.dto.ProjectMembersResponse;
import com.devtoolcopilot.project.dto.ProjectTeamCenterResponse;
import com.devtoolcopilot.project.dto.TeamGroupInviteCreateResponse;
import com.devtoolcopilot.project.entity.ProjectMemberRole;

import java.util.List;

public interface ProjectCollabService {
    ProjectMemberRole getMyRole(Long userId, Long projectId);

    void requireMember(Long userId, Long projectId);

    void requireAtLeast(Long userId, Long projectId, ProjectMemberRole minRole);

    ProjectMembersResponse members(Long userId, Long projectId);

    ProjectInviteCreateResponse invite(Long inviterUserId, Long projectId, String email, ProjectMemberRole role);

    List<ProjectInviteItem> invites(Long userId, Long projectId);

    Long acceptInvite(Long userId, String token);

    Long rejectInvite(Long userId, String token);

    ProjectInviteCreateResponse reissueInvite(Long ownerUserId, Long projectId, Long inviteId);

    void cancelInvite(Long ownerUserId, Long projectId, Long inviteId);

    void removeMember(Long ownerUserId, Long projectId, Long memberUserId);

    void updateMemberRole(Long ownerUserId, Long projectId, Long memberUserId, ProjectMemberRole role);

    void setMemberDisabled(Long ownerUserId, Long projectId, Long memberUserId, boolean disabled);

    void transferOwnership(Long ownerUserId, Long projectId, Long newOwnerUserId);

    void leaveProject(Long userId, Long projectId);

    ProjectMembersExportResponse exportMembers(Long userId, Long projectId);

    ProjectTeamCenterResponse teamCenter(Long userId, Long groupId);

    Long createTeamGroup(Long userId, String name);

    void renameTeamGroup(Long userId, Long groupId, String name);

    void deleteTeamGroup(Long userId, Long groupId);

    TeamGroupInviteCreateResponse inviteToTeamGroup(Long userId, Long groupId, String email);

    Long acceptTeamGroupInvite(Long userId, String token);

    Long rejectTeamGroupInvite(Long userId, String token);

    void cancelTeamGroupInvite(Long userId, Long groupId, Long inviteId);

    TeamGroupInviteCreateResponse reissueTeamGroupInvite(Long userId, Long groupId, Long inviteId);

    void removeTeamGroupMember(Long userId, Long groupId, Long memberUserId);

    void leaveTeamGroup(Long userId, Long groupId);

    void transferTeamGroupOwnership(Long userId, Long groupId, Long memberUserId);

    java.util.List<ProjectTeamCenterResponse.InviteItem> teamInvitesMine(Long userId);

    Long acceptTeamGroupInviteById(Long userId, Long inviteId);

    Long rejectTeamGroupInviteById(Long userId, Long inviteId);

    void deleteTeamGroupActivity(Long userId, Long groupId, Long activityId);

    int clearTeamGroupActivities(Long userId, Long groupId);

    List<ProjectActivityItem> activities(Long userId, Long projectId, Integer limit);

    void addActivity(Long projectId, Long actorUserId, String type, String detail);

    void deleteActivity(Long ownerUserId, Long projectId, Long activityId);

    int clearActivities(Long ownerUserId, Long projectId);
}
