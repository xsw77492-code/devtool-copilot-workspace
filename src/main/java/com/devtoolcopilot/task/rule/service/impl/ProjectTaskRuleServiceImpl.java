package com.devtoolcopilot.task.rule.service.impl;

import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.project.entity.ProjectMemberRole;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.task.rule.dto.ProjectTaskRuleDTO;
import com.devtoolcopilot.task.rule.entity.ProjectTaskRule;
import com.devtoolcopilot.task.rule.mapper.ProjectTaskRuleMapper;
import com.devtoolcopilot.task.rule.service.ProjectTaskRuleService;
import org.springframework.stereotype.Service;

@Service
public class ProjectTaskRuleServiceImpl implements ProjectTaskRuleService {
    private final ProjectCollabService projectCollabService;
    private final ProjectTaskRuleMapper ruleMapper;

    public ProjectTaskRuleServiceImpl(ProjectCollabService projectCollabService, ProjectTaskRuleMapper ruleMapper) {
        this.projectCollabService = projectCollabService;
        this.ruleMapper = ruleMapper;
    }

    @Override
    public ProjectTaskRuleDTO get(Long userId, Long projectId) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (projectId == null) throw new ApiException(400, "projectId不能为空");
        projectCollabService.requireMember(userId, projectId);
        ProjectTaskRule row = ruleMapper.selectById(projectId);
        ProjectTaskRuleDTO dto = new ProjectTaskRuleDTO();
        dto.setRequireChecklistDoneForDone(row != null && row.getRequireChecklistDoneForDone() != null && row.getRequireChecklistDoneForDone() == 1);
        return dto;
    }

    @Override
    public ProjectTaskRuleDTO save(Long userId, Long projectId, Boolean requireChecklistDoneForDone) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (projectId == null) throw new ApiException(400, "projectId不能为空");
        projectCollabService.requireAtLeast(userId, projectId, ProjectMemberRole.OWNER);

        int flag = Boolean.TRUE.equals(requireChecklistDoneForDone) ? 1 : 0;
        ProjectTaskRule row = ruleMapper.selectById(projectId);
        if (row == null) {
            row = new ProjectTaskRule();
            row.setProjectId(projectId);
            row.setRequireChecklistDoneForDone(flag);
            ruleMapper.insert(row);
        } else {
            row.setRequireChecklistDoneForDone(flag);
            ruleMapper.updateById(row);
        }
        ProjectTaskRuleDTO dto = new ProjectTaskRuleDTO();
        dto.setRequireChecklistDoneForDone(flag == 1);
        return dto;
    }
}
