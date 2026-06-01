package com.devtoolcopilot.integration.gitee.service;

import com.devtoolcopilot.integration.gitee.dto.GiteePanelDTO;
import com.devtoolcopilot.integration.gitee.dto.GiteeRepoConfigDTO;

public interface GiteeIntegrationService {
    GiteeRepoConfigDTO getConfig(Long userId, Long projectId);

    GiteeRepoConfigDTO saveConfig(Long userId, Long projectId, String owner, String repo, String accessToken);

    GiteePanelDTO panel(Long userId, Long projectId);

    Long linkTaskToPr(Long userId, Long projectId, Long taskId, String prInput);

    void unlink(Long userId, Long id);
}

