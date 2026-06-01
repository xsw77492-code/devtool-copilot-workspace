package com.devtoolcopilot.integration.gitee.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GiteeRepoConfigDTO {
    private Long projectId;
    private String owner;
    private String repo;
    private boolean hasToken;
}

