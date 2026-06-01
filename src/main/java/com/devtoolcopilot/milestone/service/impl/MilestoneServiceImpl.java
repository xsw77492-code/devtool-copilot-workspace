package com.devtoolcopilot.milestone.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.asset.config.AssetProperties;
import com.devtoolcopilot.asset.entity.ProjectAsset;
import com.devtoolcopilot.asset.mapper.ProjectAssetMapper;
import com.devtoolcopilot.audit.service.ProjectAuditService;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.milestone.dto.MilestonePublishResponse;
import com.devtoolcopilot.milestone.entity.ProjectMilestone;
import com.devtoolcopilot.milestone.mapper.ProjectMilestoneMapper;
import com.devtoolcopilot.milestone.service.MilestoneService;
import com.devtoolcopilot.project.entity.Project;
import com.devtoolcopilot.project.entity.ProjectMemberRole;
import com.devtoolcopilot.project.mapper.ProjectMapper;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.realtime.service.RealtimeCollabService;
import com.devtoolcopilot.task.deliverable.entity.TaskDeliverable;
import com.devtoolcopilot.task.deliverable.mapper.TaskDeliverableMapper;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.entity.TaskStatus;
import com.devtoolcopilot.task.mapper.TaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class MilestoneServiceImpl implements MilestoneService {
    private final ProjectCollabService projectCollabService;
    private final ProjectMapper projectMapper;
    private final ProjectMilestoneMapper milestoneMapper;
    private final TaskMapper taskMapper;
    private final TaskDeliverableMapper deliverableMapper;
    private final ProjectAssetMapper assetMapper;
    private final AssetProperties assetProperties;
    private final RealtimeCollabService realtimeCollabService;
    private final ProjectAuditService projectAuditService;

    public MilestoneServiceImpl(ProjectCollabService projectCollabService,
                                ProjectMapper projectMapper,
                                ProjectMilestoneMapper milestoneMapper,
                                TaskMapper taskMapper,
                                TaskDeliverableMapper deliverableMapper,
                                ProjectAssetMapper assetMapper,
                                AssetProperties assetProperties,
                                RealtimeCollabService realtimeCollabService,
                                ProjectAuditService projectAuditService) {
        this.projectCollabService = projectCollabService;
        this.projectMapper = projectMapper;
        this.milestoneMapper = milestoneMapper;
        this.taskMapper = taskMapper;
        this.deliverableMapper = deliverableMapper;
        this.assetMapper = assetMapper;
        this.assetProperties = assetProperties;
        this.realtimeCollabService = realtimeCollabService;
        this.projectAuditService = projectAuditService;
    }

    @Override
    public List<ProjectMilestone> listByProject(Long userId, Long projectId, Boolean includeArchived) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (projectId == null) throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        projectCollabService.requireMember(userId, projectId);
        boolean inc = includeArchived != null && includeArchived;
        var q = Wrappers.<ProjectMilestone>lambdaQuery()
                .eq(ProjectMilestone::getProjectId, projectId)
                .orderByDesc(ProjectMilestone::getId);
        if (!inc) {
            q.ne(ProjectMilestone::getStatus, "ARCHIVED");
        }
        return milestoneMapper.selectList(q);
    }

    @Override
    @Transactional
    public Long create(Long userId, Long projectId, String name, String description, Long dueTime) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (projectId == null) throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("NAME_REQUIRED");
        projectCollabService.requireAtLeast(userId, projectId, ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(projectId);

        ProjectMilestone m = new ProjectMilestone();
        m.setProjectId(projectId);
        m.setUserId(userId);
        m.setName(name.trim());
        if (description != null && !description.isBlank()) m.setDescription(description.trim());
        m.setStatus("OPEN");
        if (dueTime != null && dueTime != 0) {
            m.setDueTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(dueTime), ZoneId.systemDefault()));
        }
        milestoneMapper.insert(m);

        if (projectAuditService != null) {
            projectAuditService.record(projectId, userId, "MILESTONE_CREATED", "MILESTONE", m.getId(), m.getName(), "{\"milestoneId\":" + m.getId() + "}");
        }
        if (realtimeCollabService != null) {
            realtimeCollabService.broadcast(projectId, userId, "MILESTONE_CREATED", "{\"milestoneId\":" + m.getId() + "}");
        }
        return m.getId();
    }

    @Override
    @Transactional
    public MilestonePublishResponse publish(Long userId, Long milestoneId) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (milestoneId == null) throw new IllegalArgumentException("MILESTONE_ID_REQUIRED");
        ProjectMilestone m = milestoneMapper.selectById(milestoneId);
        if (m == null) return null;

        projectCollabService.requireAtLeast(userId, m.getProjectId(), ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(m.getProjectId());

        String status = m.getStatus() == null ? "" : m.getStatus().trim().toUpperCase();
        if ("ARCHIVED".equals(status)) throw new ApiException(400, "里程碑已归档");
        if ("PUBLISHED".equals(status)) {
            Long latestAssetId = saveReleaseNoteAsset(userId, m);
            milestoneMapper.update(null, Wrappers.<ProjectMilestone>lambdaUpdate()
                    .eq(ProjectMilestone::getId, m.getId())
                    .set(ProjectMilestone::getReleaseAssetId, latestAssetId)
            );
            if (realtimeCollabService != null) {
                realtimeCollabService.broadcast(m.getProjectId(), userId, "MILESTONE_PUBLISHED", "{\"milestoneId\":" + m.getId() + ",\"assetId\":" + latestAssetId + "}");
            }
            return new MilestonePublishResponse(m.getId(), latestAssetId);
        }

        Long assetId = saveReleaseNoteAsset(userId, m);
        milestoneMapper.update(null, Wrappers.<ProjectMilestone>lambdaUpdate()
                .eq(ProjectMilestone::getId, m.getId())
                .set(ProjectMilestone::getStatus, "PUBLISHED")
                .set(ProjectMilestone::getPublishedTime, LocalDateTime.now())
                .set(ProjectMilestone::getReleaseAssetId, assetId)
        );
        if (projectAuditService != null) {
            projectAuditService.record(m.getProjectId(), userId, "MILESTONE_PUBLISHED", "MILESTONE", m.getId(), m.getName(), "{\"milestoneId\":" + m.getId() + ",\"assetId\":" + assetId + "}");
        }
        if (realtimeCollabService != null) {
            realtimeCollabService.broadcast(m.getProjectId(), userId, "MILESTONE_PUBLISHED", "{\"milestoneId\":" + m.getId() + ",\"assetId\":" + assetId + "}");
        }
        return new MilestonePublishResponse(m.getId(), assetId);
    }

    @Override
    @Transactional
    public boolean archive(Long userId, Long milestoneId) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (milestoneId == null) throw new IllegalArgumentException("MILESTONE_ID_REQUIRED");
        ProjectMilestone m = milestoneMapper.selectById(milestoneId);
        if (m == null) return false;
        projectCollabService.requireAtLeast(userId, m.getProjectId(), ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(m.getProjectId());
        milestoneMapper.update(null, Wrappers.<ProjectMilestone>lambdaUpdate()
                .eq(ProjectMilestone::getId, milestoneId)
                .set(ProjectMilestone::getStatus, "ARCHIVED")
                .set(ProjectMilestone::getArchivedTime, LocalDateTime.now())
        );
        if (projectAuditService != null) {
            projectAuditService.record(m.getProjectId(), userId, "MILESTONE_ARCHIVED", "MILESTONE", m.getId(), m.getName(), "{\"milestoneId\":" + m.getId() + "}");
        }
        if (realtimeCollabService != null) {
            realtimeCollabService.broadcast(m.getProjectId(), userId, "MILESTONE_ARCHIVED", "{\"milestoneId\":" + m.getId() + "}");
        }
        return true;
    }

    @Override
    @Transactional
    public boolean unarchive(Long userId, Long milestoneId) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (milestoneId == null) throw new IllegalArgumentException("MILESTONE_ID_REQUIRED");
        ProjectMilestone m = milestoneMapper.selectById(milestoneId);
        if (m == null) return false;
        projectCollabService.requireAtLeast(userId, m.getProjectId(), ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(m.getProjectId());
        milestoneMapper.update(null, Wrappers.<ProjectMilestone>lambdaUpdate()
                .eq(ProjectMilestone::getId, milestoneId)
                .set(ProjectMilestone::getStatus, "OPEN")
                .set(ProjectMilestone::getArchivedTime, null)
        );
        if (projectAuditService != null) {
            projectAuditService.record(m.getProjectId(), userId, "MILESTONE_UNARCHIVED", "MILESTONE", m.getId(), m.getName(), "{\"milestoneId\":" + m.getId() + "}");
        }
        if (realtimeCollabService != null) {
            realtimeCollabService.broadcast(m.getProjectId(), userId, "MILESTONE_UNARCHIVED", "{\"milestoneId\":" + m.getId() + "}");
        }
        return true;
    }

    private void ensureProjectWritable(Long projectId) {
        if (projectId == null) return;
        Project p = projectMapper.selectById(projectId);
        if (p != null && p.getArchived() != null && p.getArchived() == 1) {
            throw new ApiException(400, "项目已归档");
        }
    }

    private Long saveReleaseNoteAsset(Long userId, ProjectMilestone m) {
        String md = buildReleaseNoteMarkdown(m);
        byte[] bytes = md.getBytes(StandardCharsets.UTF_8);
        String filename = buildFilename(m.getName());
        return saveAsset(userId, m.getProjectId(), "RELEASE_NOTE", filename, "text/markdown", bytes);
    }

    private String buildReleaseNoteMarkdown(ProjectMilestone m) {
        List<Task> done = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .eq(Task::getProjectId, m.getProjectId())
                .eq(Task::getMilestoneId, m.getId())
                .eq(Task::getStatus, TaskStatus.DONE)
                .orderByAsc(Task::getId)
        );
        StringBuilder sb = new StringBuilder();
        sb.append("# Release Notes: ").append(safeText(m.getName())).append("\n\n");
        sb.append("- Date: ").append(LocalDate.now()).append("\n");
        if (m.getDueTime() != null) sb.append("- Due: ").append(m.getDueTime().toLocalDate()).append("\n");
        sb.append("\n");
        String desc = safeText(m.getDescription());
        if (!desc.isBlank()) {
            sb.append(desc).append("\n\n");
        }
        sb.append("## Completed\n\n");
        if (done.isEmpty()) {
            sb.append("- (No DONE tasks in this milestone)\n");
            return sb.toString();
        }

        List<Long> taskIds = done.stream().map(Task::getId).toList();
        List<TaskDeliverable> deliverables = deliverableMapper.selectList(Wrappers.<TaskDeliverable>lambdaQuery()
                .in(TaskDeliverable::getTaskId, taskIds)
                .orderByDesc(TaskDeliverable::getId)
        );

        for (Task t : done) {
            sb.append("- ").append(safeText(t.getTitle())).append(" (#").append(t.getId()).append(")\n");
            List<TaskDeliverable> ds = new ArrayList<>();
            for (TaskDeliverable d : deliverables) {
                if (Objects.equals(d.getTaskId(), t.getId())) ds.add(d);
            }
            if (!ds.isEmpty()) {
                sb.append("  - Deliverables:\n");
                for (TaskDeliverable d : ds) {
                    String type = safeText(d.getType());
                    String title = safeText(d.getTitle());
                    String url = safeText(d.getUrl());
                    if (!url.isBlank()) {
                        sb.append("    - [").append(title).append("](").append(url).append(") (").append(type).append(")\n");
                    } else {
                        sb.append("    - ").append(title).append(" (").append(type).append(")\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    private Long saveAsset(Long userId, Long projectId, String kind, String filename, String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new ApiException(500, "生成失败");
        String baseDir = (assetProperties.getBaseDir() == null || assetProperties.getBaseDir().isBlank())
                ? "data/assets"
                : assetProperties.getBaseDir().trim();
        String ym = LocalDate.now().toString().replace("-", "").substring(0, 6);
        String key = UUID.randomUUID().toString().replace("-", "");
        String storedName = key + ".md";
        Path dir = Paths.get(baseDir, "release-note", ym).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new ApiException(500, "存储目录不可用");
        }
        Path path = dir.resolve(storedName).toAbsolutePath().normalize();
        try {
            Files.write(path, bytes);
        } catch (Exception e) {
            throw new ApiException(500, "保存失败");
        }

        ProjectAsset a = new ProjectAsset();
        a.setProjectId(projectId);
        a.setUserId(userId);
        a.setKind(kind);
        a.setName(filename);
        a.setExt("md");
        a.setContentType(contentType);
        a.setSizeBytes((long) bytes.length);
        a.setStorageKey(key);
        a.setStoragePath(path.toString());
        assetMapper.insert(a);
        return a.getId();
    }

    private String buildFilename(String milestoneName) {
        String t = safeText(milestoneName);
        if (t.isBlank()) t = "milestone";
        t = t.replaceAll("[\\\\/\\r\\n\\t]", " ").trim();
        if (t.length() > 48) t = t.substring(0, 48).trim();
        return t + "-release-notes.md";
    }

    private String safeText(String s) {
        return s == null ? "" : s.trim();
    }
}
