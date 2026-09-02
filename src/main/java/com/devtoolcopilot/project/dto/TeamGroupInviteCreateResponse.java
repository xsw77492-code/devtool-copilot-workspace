package com.devtoolcopilot.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamGroupInviteCreateResponse {
    private Long inviteId;
    private String inviteToken;
    private String inviteLink;
}
