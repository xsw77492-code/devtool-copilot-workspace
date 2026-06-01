package com.devtoolcopilot.project.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.devtoolcopilot.asset.entity.ProjectAsset;
import com.devtoolcopilot.asset.mapper.ProjectAssetMapper;
import com.devtoolcopilot.attachment.entity.TaskAttachment;
import com.devtoolcopilot.attachment.mapper.TaskAttachmentMapper;
import com.devtoolcopilot.audit.mapper.ProjectAuditLogMapper;
import com.devtoolcopilot.audit.service.ProjectAuditService;
import com.devtoolcopilot.integration.gitee.entity.GiteeRepoConfig;
import com.devtoolcopilot.integration.gitee.entity.TaskPrLink;
import com.devtoolcopilot.integration.gitee.mapper.GiteeRepoConfigMapper;
import com.devtoolcopilot.integration.gitee.mapper.TaskPrLinkMapper;
import com.devtoolcopilot.project.entity.Project;
import com.devtoolcopilot.project.entity.ProjectActivity;
import com.devtoolcopilot.project.entity.ProjectInvite;
import com.devtoolcopilot.project.entity.ProjectMember;
import com.devtoolcopilot.project.entity.ProjectMemberRole;
import com.devtoolcopilot.project.mapper.ProjectActivityMapper;
import com.devtoolcopilot.project.mapper.ProjectInviteMapper;
import com.devtoolcopilot.project.mapper.ProjectMemberMapper;
import com.devtoolcopilot.project.mapper.ProjectMapper;
import com.devtoolcopilot.project.service.ProjectService;
import com.devtoolcopilot.task.checklist.entity.TaskChecklistItem;
import com.devtoolcopilot.task.checklist.mapper.TaskChecklistItemMapper;
import com.devtoolcopilot.task.comment.entity.TaskComment;
import com.devtoolcopilot.task.comment.mapper.TaskCommentMapper;
import com.devtoolcopilot.task.deliverable.entity.TaskDeliverable;
import com.devtoolcopilot.task.deliverable.mapper.TaskDeliverableMapper;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.follow.entity.TaskFollow;
import com.devtoolcopilot.task.follow.mapper.TaskFollowMapper;
import com.devtoolcopilot.task.mapper.TaskMapper;
import com.devtoolcopilot.task.rule.entity.ProjectTaskRule;
import com.devtoolcopilot.task.rule.mapper.ProjectTaskRuleMapper;
import com.devtoolcopilot.task.template.entity.TaskTemplate;
import com.devtoolcopilot.task.template.mapper.TaskTemplateMapper;
import com.devtoolcopilot.task.timeline.entity.TaskTimeline;
import com.devtoolcopilot.task.timeline.mapper.TaskTimelineMapper;
import com.devtoolcopilot.task.view.entity.TaskBoardView;
import com.devtoolcopilot.task.view.mapper.TaskBoardViewMapper;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {
    private final TaskMapper taskMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectInviteMapper projectInviteMapper;
    private final ProjectActivityMapper projectActivityMapper;
    private final ProjectAuditLogMapper projectAuditLogMapper;
    private final ProjectAuditService projectAuditService;
    private final TaskChecklistItemMapper checklistItemMapper;
    private final TaskDeliverableMapper deliverableMapper;
    private final TaskCommentMapper commentMapper;
    private final TaskFollowMapper followMapper;
    private final TaskTimelineMapper taskTimelineMapper;
    private final TaskAttachmentMapper attachmentMapper;
    private final TaskPrLinkMapper taskPrLinkMapper;
    private final GiteeRepoConfigMapper giteeRepoConfigMapper;
    private final TaskBoardViewMapper taskBoardViewMapper;
    private final TaskTemplateMapper taskTemplateMapper;
    private final ProjectTaskRuleMapper projectTaskRuleMapper;
    private final ProjectAssetMapper projectAssetMapper;

    public ProjectServiceImpl(TaskMapper taskMapper,
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
        this.taskMapper = taskMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.projectInviteMapper = projectInviteMapper;
        this.projectActivityMapper = projectActivityMapper;
        this.projectAuditLogMapper = projectAuditLogMapper;
        this.projectAuditService = projectAuditService;
        this.checklistItemMapper = checklistItemMapper;
        this.deliverableMapper = deliverableMapper;
        this.commentMapper = commentMapper;
        this.followMapper = followMapper;
        this.taskTimelineMapper = taskTimelineMapper;
        this.attachmentMapper = attachmentMapper;
        this.taskPrLinkMapper = taskPrLinkMapper;
        this.giteeRepoConfigMapper = giteeRepoConfigMapper;
        this.taskBoardViewMapper = taskBoardViewMapper;
        this.taskTemplateMapper = taskTemplateMapper;
        this.projectTaskRuleMapper = projectTaskRuleMapper;
        this.projectAssetMapper = projectAssetMapper;
    }

    @Override
    public Long createProject(Long userId, String name, String description) {
        if (userId == null) {
            throw new IllegalArgumentException("USER_ID_REQUIRED");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("PROJECT_NAME_REQUIRED");
        }
        Project project = new Project();
        project.setUserId(userId);
        project.setName(name);
        project.setDescription(description);
        this.save(project);

        ProjectMember m = new ProjectMember();
        m.setProjectId(project.getId());
        m.setUserId(userId);
        m.setRole(ProjectMemberRole.OWNER);
        projectMemberMapper.insert(m);
        if (projectAuditService != null) {
            projectAuditService.record(project.getId(), userId, "PROJECT_CREATED", "PROJECT", project.getId(), project.getName(), "{\"projectId\":" + project.getId() + "}");
        }
        return project.getId();
    }

    @Override
    public List<Project> listByUserId(Long userId, Boolean includeArchived) {
        if (userId == null) {
            throw new IllegalArgumentException("USER_ID_REQUIRED");
        }
        int inc = includeArchived != null && includeArchived ? 1 : 0;
        return this.baseMapper.listAccessibleByUserId(userId, inc);
    }

    @Override
    @Transactional
    public boolean archiveProject(Long userId, Long projectId) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (projectId == null) throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        Project project = this.getOne(Wrappers.<Project>lambdaQuery()
                .eq(Project::getId, projectId)
                .eq(Project::getUserId, userId));
        if (project == null) return false;
        this.update(null, Wrappers.<Project>lambdaUpdate()
                .eq(Project::getId, projectId)
                .set(Project::getArchived, 1)
                .set(Project::getArchivedTime, java.time.LocalDateTime.now()));
        if (projectAuditService != null) {
            projectAuditService.record(projectId, userId, "PROJECT_ARCHIVED", "PROJECT", projectId, project.getName(), "{\"projectId\":" + projectId + "}");
        }
        return true;
    }

    @Override
    @Transactional
    public boolean unarchiveProject(Long userId, Long projectId) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (projectId == null) throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        Project project = this.getOne(Wrappers.<Project>lambdaQuery()
                .eq(Project::getId, projectId)
                .eq(Project::getUserId, userId));
        if (project == null) return false;
        this.update(null, Wrappers.<Project>lambdaUpdate()
                .eq(Project::getId, projectId)
                .set(Project::getArchived, 0)
                .set(Project::getArchivedTime, null));
        if (projectAuditService != null) {
            projectAuditService.record(projectId, userId, "PROJECT_UNARCHIVED", "PROJECT", projectId, project.getName(), "{\"projectId\":" + projectId + "}");
        }
        return true;
    }

    @Override
    @Transactional
    public boolean deleteProject(Long userId, Long projectId) {
        if (userId == null) {
            throw new IllegalArgumentException("USER_ID_REQUIRED");
        }
        if (projectId == null) {
            throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        }
        Project project = this.getOne(Wrappers.<Project>lambdaQuery()
                .eq(Project::getId, projectId)
                .eq(Project::getUserId, userId));
        if (project == null) {
            return false;
        }

        List<Long> taskIds = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                        .eq(Task::getProjectId, projectId)
                        .select(Task::getId))
                .stream()
                .map(Task::getId)
                .filter((id) -> id != null)
                .toList();

        if (attachmentMapper != null) {
            List<TaskAttachment> atts = attachmentMapper.selectList(Wrappers.<TaskAttachment>lambdaQuery().eq(TaskAttachment::getProjectId, projectId));
            if (atts != null && !atts.isEmpty()) {
                for (TaskAttachment a : atts) {
                    if (a == null) continue;
                    Path p = safePath(a.getStoragePath());
                    try {
                        if (p != null) Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                }
            }
            attachmentMapper.delete(Wrappers.<TaskAttachment>lambdaQuery().eq(TaskAttachment::getProjectId, projectId));
        }
        if (projectAssetMapper != null) {
            List<ProjectAsset> assets = projectAssetMapper.selectList(Wrappers.<ProjectAsset>lambdaQuery().eq(ProjectAsset::getProjectId, projectId));
            if (assets != null && !assets.isEmpty()) {
                for (ProjectAsset a : assets) {
                    if (a == null) continue;
                    Path p = safePath(a.getStoragePath());
                    try {
                        if (p != null) Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                }
            }
            projectAssetMapper.delete(Wrappers.<ProjectAsset>lambdaQuery().eq(ProjectAsset::getProjectId, projectId));
        }
        if (deliverableMapper != null) {
            deliverableMapper.delete(Wrappers.<TaskDeliverable>lambdaQuery().eq(TaskDeliverable::getProjectId, projectId));
        }
        if (checklistItemMapper != null) {
            checklistItemMapper.delete(Wrappers.<TaskChecklistItem>lambdaQuery().eq(TaskChecklistItem::getProjectId, projectId));
        }
        if (commentMapper != null) {
            commentMapper.delete(Wrappers.<TaskComment>lambdaQuery().eq(TaskComment::getProjectId, projectId));
        }
        if (followMapper != null) {
            if (taskIds != null && !taskIds.isEmpty()) {
                followMapper.delete(Wrappers.<TaskFollow>lambdaQuery().in(TaskFollow::getTaskId, taskIds));
            }
        }
        if (taskTimelineMapper != null) {
            taskTimelineMapper.delete(Wrappers.<TaskTimeline>lambdaQuery().eq(TaskTimeline::getProjectId, projectId));
        }
        if (taskPrLinkMapper != null) {
            taskPrLinkMapper.delete(Wrappers.<TaskPrLink>lambdaQuery().eq(TaskPrLink::getProjectId, projectId));
        }
        if (giteeRepoConfigMapper != null) {
            giteeRepoConfigMapper.delete(Wrappers.<GiteeRepoConfig>lambdaQuery().eq(GiteeRepoConfig::getProjectId, projectId));
        }
        if (taskBoardViewMapper != null) {
            taskBoardViewMapper.delete(Wrappers.<TaskBoardView>lambdaQuery().eq(TaskBoardView::getProjectId, projectId));
        }
        if (taskTemplateMapper != null) {
            taskTemplateMapper.delete(Wrappers.<TaskTemplate>lambdaQuery().eq(TaskTemplate::getProjectId, projectId));
        }
        if (projectTaskRuleMapper != null) {
            projectTaskRuleMapper.delete(Wrappers.<ProjectTaskRule>lambdaQuery().eq(ProjectTaskRule::getProjectId, projectId));
        }

        taskMapper.delete(Wrappers.<Task>lambdaQuery().eq(Task::getProjectId, projectId));
        projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery().eq(ProjectMember::getProjectId, projectId));
        projectInviteMapper.delete(Wrappers.<ProjectInvite>lambdaQuery().eq(ProjectInvite::getProjectId, projectId));
        projectActivityMapper.delete(Wrappers.<ProjectActivity>lambdaQuery().eq(ProjectActivity::getProjectId, projectId));
        if (projectAuditLogMapper != null) {
            projectAuditLogMapper.delete(Wrappers.<com.devtoolcopilot.audit.entity.ProjectAuditLog>lambdaQuery()
                    .eq(com.devtoolcopilot.audit.entity.ProjectAuditLog::getProjectId, projectId));
        }
        return this.removeById(projectId);
    }

    private static Path safePath(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            Path p = Paths.get(raw).toAbsolutePath().normalize();
            String s = p.toString().replace('\\', '/');
            if (s.contains("../") || s.contains("..\\")) return null;
            return p;
        } catch (Exception e) {
            return null;
        }
    }
}
