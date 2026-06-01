package com.devtoolcopilot.attachment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.attachment.config.AttachmentProperties;
import com.devtoolcopilot.attachment.dto.AttachmentItem;
import com.devtoolcopilot.attachment.entity.TaskAttachment;
import com.devtoolcopilot.attachment.mapper.TaskAttachmentMapper;
import com.devtoolcopilot.attachment.service.AttachmentService;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.project.entity.Project;
import com.devtoolcopilot.project.entity.ProjectMemberRole;
import com.devtoolcopilot.project.mapper.ProjectMapper;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.task.comment.entity.TaskComment;
import com.devtoolcopilot.task.comment.mapper.TaskCommentMapper;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.service.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentServiceImpl implements AttachmentService {
    private final TaskAttachmentMapper attachmentMapper;
    private final TaskService taskService;
    private final TaskCommentMapper commentMapper;
    private final ProjectCollabService projectCollabService;
    private final ProjectMapper projectMapper;
    private final AttachmentProperties attachmentProperties;

    public AttachmentServiceImpl(TaskAttachmentMapper attachmentMapper,
                                 TaskService taskService,
                                 TaskCommentMapper commentMapper,
                                 ProjectCollabService projectCollabService,
                                 ProjectMapper projectMapper,
                                 AttachmentProperties attachmentProperties) {
        this.attachmentMapper = attachmentMapper;
        this.taskService = taskService;
        this.commentMapper = commentMapper;
        this.projectCollabService = projectCollabService;
        this.projectMapper = projectMapper;
        this.attachmentProperties = attachmentProperties;
    }

    @Override
    public List<AttachmentItem> listByTask(Long userId, Long taskId) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (taskId == null) throw new ApiException(400, "taskId不能为空");
        Task task = taskService.getDetail(userId, taskId);
        if (task == null) throw new ApiException(404, "任务不存在");
        List<TaskAttachment> rows = attachmentMapper.selectList(
                Wrappers.<TaskAttachment>lambdaQuery()
                        .eq(TaskAttachment::getTaskId, taskId)
                        .orderByDesc(TaskAttachment::getId)
                        .last("LIMIT 500")
        );
        return rows.stream().map(this::toItem).toList();
    }

    @Override
    public AttachmentItem uploadToTask(Long userId, Long taskId, MultipartFile file) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (taskId == null) throw new ApiException(400, "taskId不能为空");
        Task task = taskService.getDetail(userId, taskId);
        if (task == null) throw new ApiException(404, "任务不存在");
        ensureProjectWritable(task.getProjectId());
        return uploadInternal(userId, task.getProjectId(), taskId, null, file);
    }

    @Override
    public AttachmentItem uploadToComment(Long userId, Long commentId, MultipartFile file) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (commentId == null) throw new ApiException(400, "commentId不能为空");
        TaskComment c = commentMapper.selectById(commentId);
        if (c == null) throw new ApiException(404, "评论不存在");
        projectCollabService.requireAtLeast(userId, c.getProjectId(), ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(c.getProjectId());
        return uploadInternal(userId, c.getProjectId(), c.getTaskId(), commentId, file);
    }

    @Override
    public boolean delete(Long userId, Long attachmentId) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (attachmentId == null) throw new ApiException(400, "id不能为空");
        TaskAttachment a = attachmentMapper.selectById(attachmentId);
        if (a == null) throw new ApiException(404, "附件不存在");
        projectCollabService.requireAtLeast(userId, a.getProjectId(), ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(a.getProjectId());

        Path p = safePath(a.getStoragePath());
        try {
            if (p != null) Files.deleteIfExists(p);
        } catch (Exception ignored) {
        }
        return attachmentMapper.deleteById(attachmentId) > 0;
    }

    private void ensureProjectWritable(Long projectId) {
        if (projectId == null) return;
        Project p = projectMapper.selectById(projectId);
        if (p != null && p.getArchived() != null && p.getArchived() == 1) {
            throw new ApiException(400, "项目已归档");
        }
    }

    @Override
    public DownloadFile loadDownload(Long userId, Long attachmentId) {
        return loadFile(userId, attachmentId, false);
    }

    @Override
    public DownloadFile loadPreview(Long userId, Long attachmentId) {
        return loadFile(userId, attachmentId, true);
    }

    private DownloadFile loadFile(Long userId, Long attachmentId, boolean preview) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (attachmentId == null) throw new ApiException(400, "id不能为空");
        TaskAttachment a = attachmentMapper.selectById(attachmentId);
        if (a == null) throw new ApiException(404, "附件不存在");
        projectCollabService.requireMember(userId, a.getProjectId());

        Path p = safePath(a.getStoragePath());
        if (p == null || !Files.exists(p)) throw new ApiException(404, "文件不存在");
        String ct = resolveContentType(a, p, preview);
        return new DownloadFile(a.getOriginalName(), ct, p);
    }

    private AttachmentItem uploadInternal(Long userId, Long projectId, Long taskId, Long commentId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException(400, "请选择文件");
        long size = file.getSize();
        long max = Math.max(1, (attachmentProperties.getMaxSizeMb() == null ? 50 : attachmentProperties.getMaxSizeMb())) * 1024L * 1024L;
        if (size <= 0) throw new ApiException(400, "文件为空");
        if (size > max) throw new ApiException(413, "文件过大，最大支持 " + (max / 1024 / 1024) + "MB");

        String originalName = normalizeName(file.getOriginalFilename());
        String ext = safeExt(originalName);
        if (isDangerousExt(ext)) throw new ApiException(400, "不支持该文件类型");

        String baseDir = (attachmentProperties.getBaseDir() == null || attachmentProperties.getBaseDir().isBlank())
                ? "data/attachments"
                : attachmentProperties.getBaseDir().trim();
        String ym = LocalDate.now().toString().replace("-", "").substring(0, 6);
        String key = UUID.randomUUID().toString().replace("-", "");
        String filename = key + (ext.isEmpty() ? "" : ("." + ext));
        Path dir = Paths.get(baseDir, ym).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new ApiException(500, "附件目录不可用");
        }

        Path path = dir.resolve(filename).toAbsolutePath().normalize();
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new ApiException(500, "保存失败");
        }

        TaskAttachment a = new TaskAttachment();
        a.setProjectId(projectId);
        a.setTaskId(taskId);
        a.setCommentId(commentId);
        a.setUserId(userId);
        a.setOriginalName(originalName);
        a.setContentType(file.getContentType());
        a.setSizeBytes(size);
        a.setStorageKey(key);
        a.setStoragePath(path.toString());
        attachmentMapper.insert(a);
        return toItem(a);
    }

    private AttachmentItem toItem(TaskAttachment a) {
        return new AttachmentItem(
                a.getId(),
                a.getProjectId(),
                a.getTaskId(),
                a.getCommentId(),
                a.getUserId(),
                a.getOriginalName(),
                a.getContentType(),
                a.getSizeBytes(),
                a.getCreateTime()
        );
    }

    private static String normalizeName(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isBlank()) return "file";
        v = v.replace("\\", "/");
        int idx = v.lastIndexOf('/');
        if (idx >= 0) v = v.substring(idx + 1);
        if (v.isBlank()) return "file";
        if (v.length() > 255) v = v.substring(0, 255);
        return v;
    }

    private static String safeExt(String filename) {
        if (filename == null) return "";
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) return "";
        String ext = filename.substring(idx + 1).trim().toLowerCase();
        if (ext.length() > 12) return "";
        for (int i = 0; i < ext.length(); i++) {
            char c = ext.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9')) return "";
        }
        return ext;
    }

    private static boolean isDangerousExt(String ext) {
        if (ext == null || ext.isBlank()) return false;
        Set<String> deny = Set.of("exe", "sh", "bat", "cmd", "ps1", "jar", "war", "class", "msi");
        return deny.contains(ext);
    }

    private static String resolveContentType(TaskAttachment a, Path path, boolean preview) {
        String ct = a.getContentType();
        if (ct != null && !ct.isBlank()) {
            if (!preview) return ct;
            if (isPreviewableContentType(ct)) return ct;
        }
        try {
            String guessed = Files.probeContentType(path);
            if (guessed != null && !guessed.isBlank()) {
                if (!preview) return guessed;
                if (isPreviewableContentType(guessed)) return guessed;
            }
        } catch (Exception ignored) {
        }
        String ext = safeExt(a.getOriginalName());
        String fallback = switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "pdf" -> "application/pdf";
            case "txt", "log", "md", "json", "xml", "yml", "yaml", "csv", "java", "ts", "tsx", "js", "vue", "sql" -> "text/plain; charset=UTF-8";
            default -> "application/octet-stream";
        };
        if (preview && !isPreviewableContentType(fallback)) return "application/octet-stream";
        return fallback;
    }

    private static boolean isPreviewableContentType(String ct) {
        String v = ct == null ? "" : ct.toLowerCase();
        return v.startsWith("image/") || v.startsWith("text/") || v.contains("json") || v.contains("xml") || v.equals("application/pdf");
    }

    private static Path safePath(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Paths.get(raw).toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
    }
}
