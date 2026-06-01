package com.devtoolcopilot.task.rule.service;

import com.devtoolcopilot.task.rule.dto.ProjectTaskRuleDTO;

public interface ProjectTaskRuleService {
    ProjectTaskRuleDTO get(Long userId, Long projectId);

    ProjectTaskRuleDTO save(Long userId, Long projectId, Boolean requireChecklistDoneForDone);
}

