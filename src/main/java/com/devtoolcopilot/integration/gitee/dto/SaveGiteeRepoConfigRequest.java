package com.devtoolcopilot.integration.gitee.dto;

import lombok.Data;

@Data
public class SaveGiteeRepoConfigRequest {
    private Long projectId;
    private String owner;
    private String repo;
    private String accessToken;
}

