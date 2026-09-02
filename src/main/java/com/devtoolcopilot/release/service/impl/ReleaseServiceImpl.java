package com.devtoolcopilot.release.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.devtoolcopilot.asset.config.AssetProperties;
import com.devtoolcopilot.asset.entity.ProjectAsset;
import com.devtoolcopilot.asset.mapper.ProjectAssetMapper;
import com.devtoolcopilot.audit.service.ProjectAuditService;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.milestone.entity.ProjectMilestone;
import com.devtoolcopilot.milestone.mapper.ProjectMilestoneMapper;
import com.devtoolcopilot.project.entity.Project;
import com.devtoolcopilot.project.entity.ProjectMemberRole;
import com.devtoolcopilot.project.mapper.ProjectMapper;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.realtime.service.RealtimeCollabService;
import com.devtoolcopilot.release.dto.ReleaseGenerateNotesResponse;
import com.devtoolcopilot.release.dto.ReleaseCreateRequest;
import com.devtoolcopilot.release.dto.ReleaseUpdateRequest;
import com.devtoolcopilot.release.entity.ProjectRelease;
import com.devtoolcopilot.release.mapper.ProjectReleaseMapper;
import com.devtoolcopilot.release.service.ReleaseService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ReleaseServiceImpl extends ServiceImpl<ProjectReleaseMapper, ProjectRelease> implements ReleaseService {
    private final ProjectCollabService projectCollabService;
    private final ProjectMapper projectMapper;
    private final ProjectMilestoneMapper milestoneMapper;
    private final TaskMapper taskMapper;
    private final TaskDeliverableMapper deliverableMapper;
    private final ProjectAssetMapper assetMapper;
    private final AssetProperties assetProperties;
    private final RealtimeCollabService realtimeCollabService;
    private final ProjectAuditService projectAuditService;

    public ReleaseServiceImpl(ProjectCollabService projectCollabService,
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
    public List<ProjectRelease> listByProject(Long userId, Long projectId) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (projectId == null) throw new ApiException(400, "projectId不能为空");
        projectCollabService.requireMember(userId, projectId);
        return this.baseMapper.selectList(Wrappers.<ProjectRelease>lambdaQuery()
                .eq(ProjectRelease::getProjectId, projectId)
                .orderByDesc(ProjectRelease::getId));
    }

    @Override
    public ProjectRelease get(Long userId, Long releaseId) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (releaseId == null) throw new ApiException(400, "releaseId不能为空");
        ProjectRelease r = this.getById(releaseId);
        if (r == null) return null;
        projectCollabService.requireMember(userId, r.getProjectId());
        return r;
    }

    @Override
    @Transactional
    public Long create(Long userId, ReleaseCreateRequest req) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (req == null) throw new ApiException(400, "请求不能为空");
        Long projectId = req.getProjectId();
        if (projectId == null) throw new ApiException(400, "projectId不能为空");
        String version = safeText(req.getVersion());
        if (version.isBlank()) throw new ApiException(400, "version不能为空");
        Long milestoneId = req.getMilestoneId();

        projectCollabService.requireAtLeast(userId, projectId, ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(projectId);

        if (milestoneId != null && milestoneId != 0) {
            ProjectMilestone m = milestoneMapper.selectById(milestoneId);
            if (m == null || !Objects.equals(m.getProjectId(), projectId)) throw new ApiException(404, "里程碑不存在");
        } else {
            milestoneId = null;
        }

        ProjectRelease r = new ProjectRelease();
        r.setProjectId(projectId);
        r.setMilestoneId(milestoneId);
        r.setUserId(userId);
        r.setVersion(version);
        String summary = safeText(req.getSummary());
        if (!summary.isBlank()) r.setSummary(summary);
        r.setStatus("DRAFT");
        this.save(r);

        boolean gen = req.getGenerateNotes() == null || req.getGenerateNotes();
        if (gen && r.getMilestoneId() != null) {
            Long assetId = saveReleaseNoteAsset(userId, r);
            r.setNotesAssetId(assetId);
            this.updateById(r);
        }

        if (projectAuditService != null) {
            projectAuditService.record(projectId, userId, "RELEASE_CREATED", "RELEASE", r.getId(), r.getVersion(), "{\"releaseId\":" + r.getId() + "}");
        }
        if (realtimeCollabService != null) {
            realtimeCollabService.broadcast(projectId, userId, "RELEASE_CREATED", "{\"releaseId\":" + r.getId() + "}");
        }
        return r.getId();
    }

    @Override
    @Transactional
    public boolean updateDraft(Long userId, Long releaseId, ReleaseUpdateRequest req) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (releaseId == null) throw new ApiException(400, "releaseId不能为空");
        if (req == null) throw new ApiException(400, "请求不能为空");
        ProjectRelease r = this.getById(releaseId);
        if (r == null) return false;
        projectCollabService.requireAtLeast(userId, r.getProjectId(), ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(r.getProjectId());

        String status = safeText(r.getStatus()).toUpperCase();
        if (!"DRAFT".equals(status)) throw new ApiException(400, "发布后不可修改");

        Long milestoneId = req.getMilestoneId();
        if (milestoneId != null && milestoneId != 0) {
            ProjectMilestone m = milestoneMapper.selectById(milestoneId);
            if (m == null || !Objects.equals(m.getProjectId(), r.getProjectId())) throw new ApiException(404, "里程碑不存在");
            r.setMilestoneId(milestoneId);
        } else if (milestoneId != null && milestoneId == 0) {
            r.setMilestoneId(null);
        }

        String version = safeText(req.getVersion());
        if (!version.isBlank()) r.setVersion(version);

        String summary = safeText(req.getSummary());
        r.setSummary(summary.isBlank() ? null : summary);
        return this.updateById(r);
    }

    @Override
    @Transactional
    public ReleaseGenerateNotesResponse generateNotes(Long userId, Long releaseId) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (releaseId == null) throw new ApiException(400, "releaseId不能为空");
        ProjectRelease r = this.getById(releaseId);
        if (r == null) return null;
        projectCollabService.requireAtLeast(userId, r.getProjectId(), ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(r.getProjectId());
        String status = safeText(r.getStatus()).toUpperCase();
        if (!"DRAFT".equals(status)) throw new ApiException(400, "发布后不可重新生成");
        if (r.getMilestoneId() == null) throw new ApiException(400, "请先关联里程碑");

        Long assetId = saveReleaseNoteAsset(userId, r);
        this.update(null, Wrappers.<ProjectRelease>lambdaUpdate()
                .eq(ProjectRelease::getId, releaseId)
                .set(ProjectRelease::getNotesAssetId, assetId)
        );

        if (projectAuditService != null) {
            projectAuditService.record(r.getProjectId(), userId, "RELEASE_NOTES_GENERATED", "RELEASE", r.getId(), r.getVersion(),
                    "{\"releaseId\":" + r.getId() + ",\"assetId\":" + assetId + "}");
        }
        if (realtimeCollabService != null) {
            realtimeCollabService.broadcast(r.getProjectId(), userId, "RELEASE_NOTES_GENERATED",
                    "{\"releaseId\":" + r.getId() + ",\"assetId\":" + assetId + "}");
        }
        return new ReleaseGenerateNotesResponse(r.getId(), assetId);
    }

    @Override
    @Transactional
    public boolean publish(Long userId, Long releaseId) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (releaseId == null) throw new ApiException(400, "releaseId不能为空");
        ProjectRelease r = this.getById(releaseId);
        if (r == null) return false;
        projectCollabService.requireAtLeast(userId, r.getProjectId(), ProjectMemberRole.OWNER);
        ensureProjectWritable(r.getProjectId());

        String status = safeText(r.getStatus()).toUpperCase();
        if (!"DRAFT".equals(status)) throw new ApiException(400, "已发布");

        Long assetId = r.getNotesAssetId();
        if (assetId == null) {
            if (r.getMilestoneId() == null) throw new ApiException(400, "请先生成发布说明");
            assetId = saveReleaseNoteAsset(userId, r);
        }

        this.update(null, Wrappers.<ProjectRelease>lambdaUpdate()
                .eq(ProjectRelease::getId, releaseId)
                .set(ProjectRelease::getNotesAssetId, assetId)
                .set(ProjectRelease::getStatus, "PUBLISHED")
                .set(ProjectRelease::getPublishedTime, LocalDateTime.now())
        );

        if (projectAuditService != null) {
            projectAuditService.record(r.getProjectId(), userId, "RELEASE_PUBLISHED", "RELEASE", r.getId(), r.getVersion(), "{\"releaseId\":" + r.getId() + "}");
        }
        if (realtimeCollabService != null) {
            realtimeCollabService.broadcast(r.getProjectId(), userId, "RELEASE_PUBLISHED", "{\"releaseId\":" + r.getId() + "}");
        }
        return true;
    }

    @Override
    @Transactional
    public boolean deleteDraft(Long userId, Long releaseId) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (releaseId == null) throw new ApiException(400, "releaseId不能为空");
        ProjectRelease r = this.getById(releaseId);
        if (r == null) return false;

        projectCollabService.requireAtLeast(userId, r.getProjectId(), ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(r.getProjectId());

        String status = safeText(r.getStatus()).toUpperCase();
        if (!"DRAFT".equals(status)) throw new ApiException(400, "已发布不可删除");

        ProjectMemberRole myRole = projectCollabService.getMyRole(userId, r.getProjectId());
        if (myRole != ProjectMemberRole.OWNER) {
            if (myRole != ProjectMemberRole.DEVELOPER) throw new ApiException(403, "权限不足");
            if (!Objects.equals(r.getUserId(), userId)) throw new ApiException(403, "权限不足");
        }

        if (r.getNotesAssetId() != null && assetMapper != null) {
            ProjectAsset a = assetMapper.selectById(r.getNotesAssetId());
            if (a != null) {
                Path p = safePath(a.getStoragePath());
                try {
                    if (p != null) Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
                assetMapper.deleteById(a.getId());
            }
        }

        boolean ok = this.removeById(releaseId);
        if (ok) {
            if (projectAuditService != null) {
                projectAuditService.record(r.getProjectId(), userId, "RELEASE_DELETED", "RELEASE", r.getId(), r.getVersion(), "{\"releaseId\":" + r.getId() + "}");
            }
            if (realtimeCollabService != null) {
                realtimeCollabService.broadcast(r.getProjectId(), userId, "RELEASE_DELETED", "{\"releaseId\":" + r.getId() + "}");
            }
        }
        return ok;
    }

    private void ensureProjectWritable(Long projectId) {
        if (projectId == null) return;
        Project p = projectMapper.selectById(projectId);
        if (p != null && p.getArchived() != null && p.getArchived() == 1) {
            throw new ApiException(400, "项目已归档");
        }
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

    private Long saveReleaseNoteAsset(Long userId, ProjectRelease r) {
        ProjectMilestone m = milestoneMapper.selectById(r.getMilestoneId());
        if (m == null || !Objects.equals(m.getProjectId(), r.getProjectId())) throw new ApiException(404, "里程碑不存在");
        String md = buildReleaseNoteMarkdown(r, m);
        byte[] bytes = md.getBytes(StandardCharsets.UTF_8);
        String filename = buildFilename(r.getVersion());
        return saveAsset(userId, r.getProjectId(), "RELEASE_NOTE", filename, "text/markdown", bytes);
    }

    private String buildReleaseNoteMarkdown(ProjectRelease r, ProjectMilestone m) {
        List<Task> done = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .eq(Task::getProjectId, r.getProjectId())
                .eq(Task::getMilestoneId, m.getId())
                .eq(Task::getStatus, TaskStatus.DONE)
                .orderByAsc(Task::getId)
        );

        StringBuilder sb = new StringBuilder();
        sb.append("# 发布说明 ").append(safeText(r.getVersion())).append("\n\n");
        sb.append("- 发布日期：").append(LocalDate.now()).append("\n");
        sb.append("- 里程碑：").append(safeText(m.getName())).append("\n");
        String summary = safeText(r.getSummary());
        if (!summary.isBlank()) {
            sb.append("\n");
            sb.append(summary).append("\n");
        }
        sb.append("\n");
        sb.append("## 已完成任务\n\n");
        if (done.isEmpty()) {
            sb.append("- （本里程碑暂无已完成任务）\n");
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
                sb.append("  - 交付物：\n");
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
        Path dir = Paths.get(baseDir, "release", ym).toAbsolutePath().normalize();
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

    private String buildFilename(String version) {
        String t = safeText(version);
        if (t.isBlank()) t = "release";
        t = t.replaceAll("[\\\\/\\r\\n\\t]", " ").trim();
        if (t.length() > 48) t = t.substring(0, 48).trim();
        return t + "-release-notes.md";
    }

    private String safeText(String s) {
        return s == null ? "" : s.trim();
    }
}
