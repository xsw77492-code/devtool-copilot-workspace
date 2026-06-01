package com.devtoolcopilot.project.config;

import com.devtoolcopilot.asset.mapper.ProjectAssetMapper;
import com.devtoolcopilot.attachment.mapper.TaskAttachmentMapper;
import com.devtoolcopilot.integration.gitee.mapper.GiteeRepoConfigMapper;
import com.devtoolcopilot.integration.gitee.mapper.TaskPrLinkMapper;
import com.devtoolcopilot.audit.mapper.ProjectAuditLogMapper;
import com.devtoolcopilot.audit.service.ProjectAuditService;
import com.devtoolcopilot.project.mapper.ProjectActivityMapper;
import com.devtoolcopilot.project.mapper.ProjectInviteMapper;
import com.devtoolcopilot.project.mapper.ProjectMemberMapper;
import com.devtoolcopilot.project.service.ProjectService;
import com.devtoolcopilot.project.service.impl.ProjectServiceImpl;
import com.devtoolcopilot.task.checklist.mapper.TaskChecklistItemMapper;
import com.devtoolcopilot.task.comment.mapper.TaskCommentMapper;
import com.devtoolcopilot.task.deliverable.mapper.TaskDeliverableMapper;
import com.devtoolcopilot.task.follow.mapper.TaskFollowMapper;
import com.devtoolcopilot.task.mapper.TaskMapper;
import com.devtoolcopilot.task.rule.mapper.ProjectTaskRuleMapper;
import com.devtoolcopilot.task.template.mapper.TaskTemplateMapper;
import com.devtoolcopilot.task.timeline.mapper.TaskTimelineMapper;
import com.devtoolcopilot.task.view.mapper.TaskBoardViewMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProjectServiceConfig {
    @Bean
    public ProjectService projectService(TaskMapper taskMapper,
                                         ProjectMemberMapper projectMemberMapper,
                                         ProjectInviteMapper projectInviteMapper,
                                         ProjectActivityMapper projectActivityMapper,
                                         ProjectAuditLogMapper projectAuditLogMapper,
                                         ProjectAuditService projectAuditService,
                                         TaskChecklistItemMapper checklistItemMapper,
                                         TaskDeliverableMapper deliverableMapper,
                                         TaskCommentMapper commentMapper,
                                         TaskFollowMapper followMapper,
                                         TaskTimelineMapper taskTimelineMapper,
                                         TaskAttachmentMapper attachmentMapper,
                                         TaskPrLinkMapper taskPrLinkMapper,
                                         GiteeRepoConfigMapper giteeRepoConfigMapper,
                                         TaskBoardViewMapper taskBoardViewMapper,
                                         TaskTemplateMapper taskTemplateMapper,
                                         ProjectTaskRuleMapper projectTaskRuleMapper,
                                         ProjectAssetMapper projectAssetMapper) {
        return new ProjectServiceImpl(
                taskMapper,
                projectMemberMapper,
                projectInviteMapper,
                projectActivityMapper,
                projectAuditLogMapper,
                projectAuditService,
                checklistItemMapper,
                deliverableMapper,
                commentMapper,
                followMapper,
                taskTimelineMapper,
                attachmentMapper,
                taskPrLinkMapper,
                giteeRepoConfigMapper,
                taskBoardViewMapper,
                taskTemplateMapper,
                projectTaskRuleMapper,
                projectAssetMapper
        );
    }
}
