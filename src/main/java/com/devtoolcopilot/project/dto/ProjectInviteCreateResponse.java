package com.devtoolcopilot.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProjectInviteCreateResponse {
    private Long inviteId;
    private String inviteToken;
    private String inviteLink;
}

