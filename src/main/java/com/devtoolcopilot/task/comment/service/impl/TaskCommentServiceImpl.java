package com.devtoolcopilot.task.comment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.audit.service.ProjectAuditService;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.notification.service.NotificationService;
import com.devtoolcopilot.project.dto.ProjectMemberItem;
import com.devtoolcopilot.project.dto.ProjectMembersResponse;
import com.devtoolcopilot.project.entity.Project;
import com.devtoolcopilot.project.entity.ProjectMemberRole;
import com.devtoolcopilot.project.mapper.ProjectMapper;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.realtime.service.RealtimeCollabService;
import com.devtoolcopilot.task.comment.dto.TaskCommentDTO;
import com.devtoolcopilot.task.comment.entity.TaskComment;
import com.devtoolcopilot.task.comment.mapper.TaskCommentMapper;
import com.devtoolcopilot.task.comment.service.TaskCommentService;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.service.TaskService;
import com.devtoolcopilot.task.timeline.entity.TaskTimelineType;
import com.devtoolcopilot.task.timeline.service.TaskTimelineService;
import com.devtoolcopilot.user.entity.User;
import com.devtoolcopilot.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class TaskCommentServiceImpl implements TaskCommentService {
    private static final Pattern MENTION = Pattern.compile("@([\\p{L}0-9_\\-]{1,32})");

    private final TaskCommentMapper commentMapper;
    private final TaskService taskService;
    private final ProjectCollabService projectCollabService;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final TaskTimelineService timelineService;
    private final RealtimeCollabService realtimeCollabService;
    private final NotificationService notificationService;
    private final ProjectAuditService projectAuditService;

    public TaskCommentServiceImpl(TaskCommentMapper commentMapper,
                                  TaskService taskService,
                                  ProjectCollabService projectCollabService,
                                  ProjectMapper projectMapper,
                                  UserMapper userMapper,
                                  TaskTimelineService timelineService,
                                  RealtimeCollabService realtimeCollabService,
                                  NotificationService notificationService,
                                  ProjectAuditService projectAuditService) {
        this.commentMapper = commentMapper;
        this.taskService = taskService;
        this.projectCollabService = projectCollabService;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
        this.timelineService = timelineService;
        this.realtimeCollabService = realtimeCollabService;
        this.notificationService = notificationService;
        this.projectAuditService = projectAuditService;
    }

    @Override
    public List<TaskCommentDTO> listByTask(Long userId, Long taskId) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (taskId == null) throw new IllegalArgumentException("TASK_ID_REQUIRED");
        Task task = taskService.getDetail(userId, taskId);
        if (task == null) return List.of();

        List<TaskComment> list = commentMapper.selectList(Wrappers.<TaskComment>lambdaQuery()
                .eq(TaskComment::getTaskId, taskId)
                .orderByAsc(TaskComment::getId));
        if (list.isEmpty()) return List.of();

        Set<Long> uids = new HashSet<>();
        for (TaskComment c : list) {
            if (c.getUserId() != null) uids.add(c.getUserId());
        }
        Map<Long, String> names = new HashMap<>();
        if (!uids.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(uids);
            for (User u : users) {
                if (u != null && u.getId() != null) names.put(u.getId(), u.getUsername());
            }
        }

        List<TaskCommentDTO> out = new ArrayList<>(list.size());
        for (TaskComment c : list) {
            out.add(new TaskCommentDTO(
                    c.getId(),
                    c.getProjectId(),
                    c.getTaskId(),
                    c.getUserId(),
                    names.getOrDefault(c.getUserId(), "User"),
                    c.getContent(),
                    c.getReplyToId(),
                    c.getCreateTime()
            ));
        }
        return out;
    }

    @Override
    public Long addComment(Long userId, Long taskId, String content, Long replyToId) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (taskId == null) throw new IllegalArgumentException("TASK_ID_REQUIRED");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("CONTENT_REQUIRED");

        Task task = taskService.getDetail(userId, taskId);
        if (task == null) throw new IllegalArgumentException("TASK_NOT_FOUND");
        Long projectId = task.getProjectId();
        projectCollabService.requireAtLeast(userId, projectId, ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(projectId);

        TaskComment row = new TaskComment();
        row.setProjectId(projectId);
        row.setTaskId(taskId);
        row.setUserId(userId);
        row.setContent(content.trim());
        row.setReplyToId(replyToId);
        commentMapper.insert(row);

        String brief = row.getContent();
        if (brief.length() > 80) brief = brief.substring(0, 80) + "…";
        timelineService.addEvent(userId, projectId, taskId, TaskTimelineType.COMMENT, "评论", brief);

        String payload = "{\"projectId\":" + projectId + ",\"taskId\":" + taskId + ",\"commentId\":" + row.getId() + "}";
        projectCollabService.addActivity(projectId, userId, "TASK_COMMENT_CREATED", payload);
        realtimeCollabService.broadcast(projectId, userId, "TASK_COMMENT_CREATED", payload);
        if (projectAuditService != null) {
            projectAuditService.record(projectId, userId, "TASK_COMMENT_CREATED", "TASK", taskId, task.getTitle(), payload);
        }

        ProjectMembersResponse members = projectCollabService.members(userId, projectId);
        Map<String, Long> nameToId = new HashMap<>();
        for (ProjectMemberItem m : members.getMembers()) {
            if (m == null || m.getUserId() == null) continue;
            if (m.getUsername() != null && !m.getUsername().isBlank()) {
                nameToId.put(m.getUsername(), m.getUserId());
            }
        }

        Set<Long> mentioned = new HashSet<>();
        var matcher = MENTION.matcher(row.getContent());
        while (matcher.find()) {
            String uname = matcher.group(1);
            if (uname == null || uname.isBlank()) continue;
            Long uid = nameToId.get(uname);
            if (uid != null) mentioned.add(uid);
        }

        Long repliedUserId = null;
        if (replyToId != null) {
            TaskComment parent = commentMapper.selectById(replyToId);
            if (parent != null
                    && Objects.equals(parent.getTaskId(), taskId)
                    && parent.getUserId() != null
                    && !Objects.equals(parent.getUserId(), userId)) {
                repliedUserId = parent.getUserId();
            }
        }

        for (ProjectMemberItem m : members.getMembers()) {
            if (m == null || m.getUserId() == null) continue;
            if (Objects.equals(m.getUserId(), userId)) continue;
            if (mentioned.contains(m.getUserId())) {
                notificationService.create(m.getUserId(), "TASK_MENTION", "有人@你", brief, payload, projectId, taskId, row.getId(), "TASK_MENTION:" + taskId);
            } else if (repliedUserId != null && Objects.equals(m.getUserId(), repliedUserId)) {
                notificationService.create(m.getUserId(), "TASK_REPLY", "有人回复你", brief, payload, projectId, taskId, row.getId(), "TASK_REPLY:" + taskId);
            } else {
                notificationService.create(m.getUserId(), "TASK_COMMENT", "任务有新评论", brief, payload, projectId, taskId, row.getId(), "TASK_COMMENT:" + taskId);
            }
        }

        return row.getId();
    }

    private void ensureProjectWritable(Long projectId) {
        if (projectId == null) return;
        Project p = projectMapper.selectById(projectId);
        if (p != null && p.getArchived() != null && p.getArchived() == 1) {
            throw new ApiException(400, "项目已归档");
        }
    }
}
