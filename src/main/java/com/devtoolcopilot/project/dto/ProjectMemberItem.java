package com.devtoolcopilot.project.dto;

import com.devtoolcopilot.project.entity.ProjectMemberRole;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ProjectMemberItem {
    private Long userId;
    private String username;
    private String email;
    private ProjectMemberRole role;
    private Integer disabled;
    private LocalDateTime disabledTime;
    private LocalDateTime lastSeenAt;
    private Integer online;
    private LocalDateTime joinedAt;
}
