package com.devtoolcopilot.project.dto;

import com.devtoolcopilot.project.entity.ProjectInviteStatus;
import com.devtoolcopilot.project.entity.ProjectMemberRole;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ProjectInviteItem {
    private Long id;
    private String email;
    private ProjectMemberRole role;
    private ProjectInviteStatus status;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
}

