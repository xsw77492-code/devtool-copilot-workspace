package com.devtoolcopilot.project.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTeamCenterResponse {
    private TeamDigest digest;
    private List<ContactItem> contacts;
    private List<GroupItem> groups;
    private List<InviteItem> invites;
    private List<ActivityItem> activities;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamDigest {
        private int memberCount;
        private int onlineCount;
        private int ownerCount;
        private int disabledCount;
        private int pendingInviteCount;
        private int groupCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContactItem {
        private Long userId;
        private String username;
        private String email;
        private List<String> roles;
        private List<Long> groupIds;
        private List<String> groupNames;
        private Integer groupCount;
        private Integer online;
        private Integer disabled;
        private LocalDateTime lastSeenAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupItem {
        private Long groupId;
        private String groupName;
        private String myRole;
        private Long ownerUserId;
        private boolean systemGroup;
        private int memberCount;
        private int onlineCount;
        private int ownerCount;
        private int pendingInviteCount;
        private String latestSignal;
        private List<String> topMembers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InviteItem {
        private Long id;
        private Long groupId;
        private String groupName;
        private String email;
        private String role;
        private String status;
        private LocalDateTime expireTime;
        private LocalDateTime createTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityItem {
        private Long id;
        private Long groupId;
        private String groupName;
        private String actorUsername;
        private String type;
        private String detail;
        private LocalDateTime createTime;
    }
}
