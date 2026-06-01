package com.devtoolcopilot.task.service.impl;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.devtoolcopilot.audit.service.ProjectAuditService;
import com.devtoolcopilot.attachment.entity.TaskAttachment;
import com.devtoolcopilot.attachment.mapper.TaskAttachmentMapper;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.inbox.service.InboxService;
import com.devtoolcopilot.integration.gitee.entity.TaskPrLink;
import com.devtoolcopilot.integration.gitee.mapper.TaskPrLinkMapper;
import com.devtoolcopilot.project.entity.Project;
import com.devtoolcopilot.project.entity.ProjectMemberRole;
import com.devtoolcopilot.project.mapper.ProjectMapper;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.realtime.service.RealtimeCollabService;
import com.devtoolcopilot.notification.service.NotificationService;
import com.devtoolcopilot.task.checklist.mapper.TaskChecklistItemMapper;
import com.devtoolcopilot.task.comment.entity.TaskComment;
import com.devtoolcopilot.task.comment.mapper.TaskCommentMapper;
import com.devtoolcopilot.task.deliverable.entity.TaskDeliverable;
import com.devtoolcopilot.task.deliverable.mapper.TaskDeliverableMapper;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.entity.TaskStatus;
import com.devtoolcopilot.task.follow.entity.TaskFollow;
import com.devtoolcopilot.task.follow.mapper.TaskFollowMapper;
import com.devtoolcopilot.task.follow.service.TaskFollowService;
import com.devtoolcopilot.task.mapper.TaskMapper;
import com.devtoolcopilot.task.rule.entity.ProjectTaskRule;
import com.devtoolcopilot.task.rule.mapper.ProjectTaskRuleMapper;
import com.devtoolcopilot.task.service.TaskService;
import com.devtoolcopilot.task.timeline.entity.TaskTimeline;
import com.devtoolcopilot.task.timeline.entity.TaskTimelineType;
import com.devtoolcopilot.task.timeline.mapper.TaskTimelineMapper;
import com.devtoolcopilot.task.timeline.service.TaskTimelineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements TaskService {
    private final TaskTimelineService timelineService;
    private final ProjectCollabService projectCollabService;
    private final RealtimeCollabService realtimeCollabService;
    private final NotificationService notificationService;
    private final TaskFollowService taskFollowService;
    private final InboxService inboxService;
    private final ProjectAuditService projectAuditService;
    private final TaskChecklistItemMapper checklistItemMapper;
    private final ProjectTaskRuleMapper projectTaskRuleMapper;
    private final TaskDeliverableMapper deliverableMapper;
    private final TaskCommentMapper commentMapper;
    private final TaskFollowMapper followMapper;
    private final TaskTimelineMapper timelineMapper;
    private final TaskAttachmentMapper attachmentMapper;
    private final TaskPrLinkMapper taskPrLinkMapper;
    private final ProjectMapper projectMapper;

    public TaskServiceImpl(TaskTimelineService timelineService,
                           ProjectCollabService projectCollabService,
                           RealtimeCollabService realtimeCollabService,
                           NotificationService notificationService,
                           TaskFollowService taskFollowService,
                           InboxService inboxService,
                           ProjectAuditService projectAuditService,
                           TaskChecklistItemMapper checklistItemMapper,
                           ProjectTaskRuleMapper projectTaskRuleMapper,
                           TaskDeliverableMapper deliverableMapper,
                           TaskCommentMapper commentMapper,
                           TaskFollowMapper followMapper,
                           TaskTimelineMapper timelineMapper,
                           TaskAttachmentMapper attachmentMapper,
                           TaskPrLinkMapper taskPrLinkMapper,
                           ProjectMapper projectMapper) {
        this.timelineService = timelineService;
        this.projectCollabService = projectCollabService;
        this.realtimeCollabService = realtimeCollabService;
        this.notificationService = notificationService;
        this.taskFollowService = taskFollowService;
        this.inboxService = inboxService;
        this.projectAuditService = projectAuditService;
        this.checklistItemMapper = checklistItemMapper;
        this.projectTaskRuleMapper = projectTaskRuleMapper;
        this.deliverableMapper = deliverableMapper;
        this.commentMapper = commentMapper;
        this.followMapper = followMapper;
        this.timelineMapper = timelineMapper;
        this.attachmentMapper = attachmentMapper;
        this.taskPrLinkMapper = taskPrLinkMapper;
        this.projectMapper = projectMapper;
    }

    @Override
    public Long createTask(Long userId,
                           Long projectId,
                           String title,
                           String description,
                           String acceptanceCriteria,
                           String priority,
                           String tags,
                           String assignee,
                           Long assigneeId,
                           Long dueTime,
                           Long milestoneId,
                           Long parentTaskId,
                           String type,
                           String source) {
        if (userId == null) {
            throw new IllegalArgumentException("USER_ID_REQUIRED");
        }
        if (projectId == null) {
            throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("TITLE_REQUIRED");
        }
        projectCollabService.requireAtLeast(userId, projectId, ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(projectId);
        Task parent = null;
        if (parentTaskId != null && parentTaskId != 0) {
            parent = this.getById(parentTaskId);
            if (parent == null || !Objects.equals(parent.getProjectId(), projectId)) {
                throw new IllegalArgumentException("PARENT_TASK_INVALID");
            }
        }
        Task task = new Task();
        task.setProjectId(projectId);
        task.setTitle(title.trim());
        task.setStatus(TaskStatus.TODO);
        task.setBoardSort(nextBoardSort(projectId, TaskStatus.TODO));
        if (parent != null) {
            task.setParentTaskId(parent.getId());
            task.setType("SUBTASK");
            if (milestoneId == null || milestoneId == 0) {
                milestoneId = parent.getMilestoneId();
            }
        } else {
            String t = type == null ? "" : type.trim().toUpperCase();
            task.setType("EPIC".equals(t) ? "EPIC" : "TASK");
        }
        if (milestoneId != null && milestoneId != 0) {
            task.setMilestoneId(milestoneId);
        }
        if (description != null) task.setDescription(description);
        if (acceptanceCriteria != null) task.setAcceptanceCriteria(acceptanceCriteria);
        if (priority != null) task.setPriority(priority);
        if (tags != null) task.setTags(tags);
        if (assignee != null) task.setAssignee(assignee);
        if (assigneeId != null && assigneeId != 0) task.setAssigneeId(assigneeId);
        if (dueTime != null && dueTime != 0) {
            task.setDueTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(dueTime), ZoneId.systemDefault()));
        }
        this.save(task);
        if (projectAuditService != null) {
            projectAuditService.record(projectId, userId, "TASK_CREATED", "TASK", task.getId(), task.getTitle(), "{\"taskId\":" + task.getId() + "}");
        }
        timelineService.addEvent(userId, projectId, task.getId(), TaskTimelineType.CREATED, "创建任务", title);
        if ("AI".equalsIgnoreCase(source)) {
            projectCollabService.addActivity(projectId, userId, "AI_GENERATE_TASK", "{\"taskId\":" + task.getId() + "}");
            realtimeCollabService.broadcast(projectId, userId, "AI_GENERATE_TASK", "{\"taskId\":" + task.getId() + "}");
        } else {
            projectCollabService.addActivity(projectId, userId, "TASK_CREATED", "{\"taskId\":" + task.getId() + "}");
            realtimeCollabService.broadcast(projectId, userId, "TASK_CREATED", "{\"taskId\":" + task.getId() + "}");
        }
        if (assigneeId != null && assigneeId != 0 && !Objects.equals(assigneeId, userId)) {
            try {
                if (inboxService != null) inboxService.upsertAssignedTask(assigneeId, projectId, task.getId(), task.getTitle());
            } catch (Exception ignored) {
            }
        }
        return task.getId();
    }

    @Override
    public List<Task> kanbanList(Long userId, Long projectId) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (projectId == null) throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        projectCollabService.requireMember(userId, projectId);
        return this.list(Wrappers.<Task>lambdaQuery()
                .eq(Task::getProjectId, projectId)
                .orderByDesc(Task::getBoardSort)
                .orderByDesc(Task::getId));
    }

    @Override
    public List<Task> myWork(Long userId, List<Long> projectIds, int limit) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (projectIds == null || projectIds.isEmpty()) return new ArrayList<>();
        int lim = Math.max(1, Math.min(limit, 20));
        return this.list(Wrappers.<Task>lambdaQuery()
                .in(Task::getProjectId, projectIds)
                .eq(Task::getAssigneeId, userId)
                .ne(Task::getStatus, TaskStatus.DONE)
                .last("ORDER BY update_time DESC, id DESC LIMIT " + lim));
    }

    @Override
    public boolean kanbanMove(Long userId,
                              Long projectId,
                              Long taskId,
                              TaskStatus toStatus,
                              Long beforeId,
                              Long afterId,
                              String baseUpdatedAt,
                              Boolean forceDone) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (projectId == null) throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        if (taskId == null) throw new IllegalArgumentException("TASK_ID_REQUIRED");

        Task existing = this.getById(taskId);
        if (existing == null) return false;
        if (!Objects.equals(existing.getProjectId(), projectId)) throw new IllegalArgumentException("PROJECT_ID_INVALID");

        projectCollabService.requireAtLeast(userId, projectId, ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(projectId);

        if (baseUpdatedAt != null && !baseUpdatedAt.isBlank() && existing.getUpdatedAt() != null) {
            LocalDateTime base = parseBaseUpdatedAt(baseUpdatedAt.trim());
            if (base != null && !existing.getUpdatedAt().equals(base)) {
                throw new IllegalArgumentException("TASK_CONFLICT");
            }
        }

        TaskStatus from = existing.getStatus();
        TaskStatus targetStatus = toStatus == null ? from : toStatus;
        enforceDoneChecklistRule(userId, projectId, taskId, from, targetStatus, forceDone);

        Long beforeSort = resolveKanbanRefSort(projectId, targetStatus, beforeId, taskId);
        Long afterSort = resolveKanbanRefSort(projectId, targetStatus, afterId, taskId);

        long newSort = computeKanbanSort(projectId, targetStatus, beforeSort, afterSort, taskId);
        if (beforeSort != null && afterSort != null && Math.abs((beforeSort - afterSort)) <= 1) {
            reindexKanbanColumn(projectId, targetStatus);
            beforeSort = resolveKanbanRefSort(projectId, targetStatus, beforeId, taskId);
            afterSort = resolveKanbanRefSort(projectId, targetStatus, afterId, taskId);
            newSort = computeKanbanSort(projectId, targetStatus, beforeSort, afterSort, taskId);
        }

        existing.setStatus(targetStatus);
        existing.setBoardSort(newSort);

        boolean ok = this.updateById(existing);
        if (ok) {
            String detail = (from == null ? "" : from.name()) + " → " + targetStatus.name();
            timelineService.addEvent(userId, projectId, existing.getId(), TaskTimelineType.UPDATED, "看板移动", detail);
            if (projectAuditService != null) {
                projectAuditService.record(projectId, userId, "TASK_MOVED", "TASK", existing.getId(), existing.getTitle(), "{\"taskId\":" + existing.getId() + ",\"from\":\"" + (from == null ? "" : from.name()) + "\",\"to\":\"" + targetStatus.name() + "\"}");
            }
            realtimeCollabService.broadcast(projectId, userId, "TASK_MOVED",
                    "{\"taskId\":" + existing.getId() + ",\"from\":\"" + (from == null ? "" : from.name()) + "\",\"to\":\"" + targetStatus.name() + "\"}");
            if (from != null && from != targetStatus) {
                realtimeCollabService.broadcast(projectId, userId, "TASK_STATUS_UPDATED",
                        "{\"taskId\":" + existing.getId() + ",\"from\":\"" + from.name() + "\",\"to\":\"" + targetStatus.name() + "\"}");
            }
        }
        return ok;
    }

    @Override
    public boolean updateStatus(Long userId, Long taskId, TaskStatus status, String baseUpdatedAt, Boolean forceDone) {
        if (userId == null) {
            throw new IllegalArgumentException("USER_ID_REQUIRED");
        }
        if (taskId == null) {
            throw new IllegalArgumentException("TASK_ID_REQUIRED");
        }
        if (status == null) {
            throw new IllegalArgumentException("STATUS_REQUIRED");
        }
        Task existing = this.getById(taskId);
        if (existing == null) {
            return false;
        }
        Long beforeAssigneeId = existing.getAssigneeId();
        projectCollabService.requireAtLeast(userId, existing.getProjectId(), ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(existing.getProjectId());
        if (baseUpdatedAt != null && !baseUpdatedAt.isBlank() && existing.getUpdatedAt() != null) {
            LocalDateTime base = parseBaseUpdatedAt(baseUpdatedAt.trim());
            if (base != null && !existing.getUpdatedAt().equals(base)) {
                throw new IllegalArgumentException("TASK_CONFLICT");
            }
        }
        TaskStatus from = existing.getStatus();
        enforceDoneChecklistRule(userId, existing.getProjectId(), taskId, from, status, forceDone);
        existing.setStatus(status);
        if (status == TaskStatus.DOING && existing.getStartedTime() == null) {
            existing.setStartedTime(LocalDateTime.now());
        }
        if (status == TaskStatus.DONE) {
            if (existing.getStartedTime() == null) {
                existing.setStartedTime(existing.getCreateTime() == null ? LocalDateTime.now() : existing.getCreateTime());
            }
            if (existing.getDoneTime() == null) {
                existing.setDoneTime(LocalDateTime.now());
            }
        }
        boolean ok = this.updateById(existing);
        if (ok) {
            String detail = (from == null ? "" : from.name()) + " → " + status.name();
            timelineService.addEvent(userId, existing.getProjectId(), existing.getId(), TaskTimelineType.STATUS_CHANGED, "状态变更", detail);
            if (projectAuditService != null) {
                projectAuditService.record(existing.getProjectId(), userId, "TASK_STATUS_CHANGED", "TASK", existing.getId(), existing.getTitle(), "{\"taskId\":" + existing.getId() + ",\"from\":\"" + (from == null ? "" : from.name()) + "\",\"to\":\"" + status.name() + "\"}");
            }
            if (status == TaskStatus.DONE) {
                projectCollabService.addActivity(existing.getProjectId(), userId, "TASK_DONE", "{\"taskId\":" + existing.getId() + "}");
            }
            notifyFollowersStatus(userId, existing, from, status);
            realtimeCollabService.broadcast(existing.getProjectId(), userId, "TASK_STATUS_UPDATED",
                    "{\"taskId\":" + existing.getId() + ",\"from\":\"" + (from == null ? "" : from.name()) + "\",\"to\":\"" + status.name() + "\"}");
        }
        return ok;
    }

    private long nextBoardSort(Long projectId, TaskStatus status) {
        Task top = this.getOne(Wrappers.<Task>lambdaQuery()
                .eq(Task::getProjectId, projectId)
                .eq(Task::getStatus, status)
                .orderByDesc(Task::getBoardSort)
                .orderByDesc(Task::getId)
                .last("LIMIT 1"));
        if (top == null || top.getId() == null) return 1000L;
        long base = (top.getBoardSort() == null || top.getBoardSort() == 0) ? top.getId() : top.getBoardSort();
        return base + 1000L;
    }

    private Long resolveKanbanRefSort(Long projectId, TaskStatus status, Long refTaskId, Long movingTaskId) {
        if (refTaskId == null) return null;
        if (movingTaskId != null && movingTaskId.equals(refTaskId)) return null;
        Task ref = this.getById(refTaskId);
        if (ref == null) return null;
        if (!Objects.equals(ref.getProjectId(), projectId)) return null;
        if (status != null && ref.getStatus() != status) return null;
        if (ref.getId() == null) return null;
        if (ref.getBoardSort() == null || ref.getBoardSort() == 0) return ref.getId();
        return ref.getBoardSort();
    }

    private long computeKanbanSort(Long projectId, TaskStatus status, Long beforeSort, Long afterSort, Long movingTaskId) {
        if (beforeSort != null && afterSort != null) {
            long b = beforeSort;
            long a = afterSort;
            if (b < a) {
                long tmp = b;
                b = a;
                a = tmp;
            }
            long mid = (b + a) / 2;
            if (mid == b || mid == a) return b - 1;
            return mid;
        }
        if (beforeSort != null) return beforeSort - 1000L;
        if (afterSort != null) return afterSort + 1000L;

        Task top = this.getOne(Wrappers.<Task>lambdaQuery()
                .eq(Task::getProjectId, projectId)
                .eq(Task::getStatus, status)
                .ne(movingTaskId != null, Task::getId, movingTaskId)
                .orderByDesc(Task::getBoardSort)
                .orderByDesc(Task::getId)
                .last("LIMIT 1"));
        if (top == null || top.getId() == null) return 1000L;
        long base = (top.getBoardSort() == null || top.getBoardSort() == 0) ? top.getId() : top.getBoardSort();
        return base + 1000L;
    }

    private void reindexKanbanColumn(Long projectId, TaskStatus status) {
        List<Task> col = this.list(Wrappers.<Task>lambdaQuery()
                .eq(Task::getProjectId, projectId)
                .eq(Task::getStatus, status)
                .orderByDesc(Task::getBoardSort)
                .orderByDesc(Task::getId));
        if (col == null || col.isEmpty()) return;
        long start = (long) col.size() * 1000L;
        for (int i = 0; i < col.size(); i++) {
            Task t = col.get(i);
            if (t == null) continue;
            t.setBoardSort(start - (long) i * 1000L);
        }
        this.updateBatchById(col, 200);
    }

    private void enforceDoneChecklistRule(Long userId,
                                          Long projectId,
                                          Long taskId,
                                          TaskStatus from,
                                          TaskStatus to,
                                          Boolean forceDone) {
        if (projectId == null || taskId == null) return;
        if (to != TaskStatus.DONE) return;
        if (from == TaskStatus.DONE) return;
        if (Boolean.TRUE.equals(forceDone)) return;
        if (projectTaskRuleMapper == null || checklistItemMapper == null) return;

        ProjectTaskRule rule = projectTaskRuleMapper.selectById(projectId);
        if (rule == null || rule.getRequireChecklistDoneForDone() == null || rule.getRequireChecklistDoneForDone() != 1) return;

        Long undone = checklistItemMapper.countUndone(taskId);
        long remain = undone == null ? 0 : undone;
        if (remain <= 0) return;
        throw new ApiException(409, "验收清单未全部完成（剩余 " + remain + " 项），请完成后再标记 DONE，或二次确认强制完成");
    }

    private void notifyFollowersStatus(Long actorUserId, Task task, TaskStatus from, TaskStatus to) {
        if (notificationService == null || taskFollowService == null) return;
        if (task == null || task.getId() == null || task.getProjectId() == null) return;
        String detail = (from == null ? "" : from.name()) + " → " + (to == null ? "" : to.name());
        String title = "关注的任务有更新";
        String content = (task.getTitle() == null ? "" : task.getTitle()) + (detail.isBlank() ? "" : (" · " + detail));
        String payload = "{\"projectId\":" + task.getProjectId() + ",\"taskId\":" + task.getId() + "}";
        List<Long> followers = taskFollowService.followerIds(task.getId());
        if (followers == null || followers.isEmpty()) return;
        for (Long uid : followers) {
            if (uid == null) continue;
            if (actorUserId != null && actorUserId.equals(uid)) continue;
            try {
                notificationService.create(uid, "TASK_FOLLOW_UPDATE", title, content, payload, task.getProjectId(), task.getId(), null, "TASK_FOLLOW_UPDATE:" + task.getId());
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public int batchUpdateStatus(Long userId, List<Long> taskIds, TaskStatus status, Boolean forceDone) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (taskIds == null || taskIds.isEmpty()) throw new IllegalArgumentException("TASK_IDS_REQUIRED");
        if (status == null) throw new IllegalArgumentException("STATUS_REQUIRED");
        int ok = 0;
        for (Long id : taskIds) {
            if (id == null) continue;
            try {
                if (updateStatus(userId, id, status, null, forceDone)) ok++;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return ok;
    }

    @Override
    public int batchUpdateFields(Long userId,
                                 List<Long> taskIds,
                                 String priority,
                                 Long assigneeId,
                                 Long dueTime,
                                 Boolean clearAssignee,
                                 Boolean clearDueTime) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (taskIds == null || taskIds.isEmpty()) throw new IllegalArgumentException("TASK_IDS_REQUIRED");
        int ok = 0;
        for (Long id : taskIds) {
            if (id == null) continue;
            Task existing = this.getById(id);
            if (existing == null) continue;
            Long beforeAssigneeId = existing.getAssigneeId();
            projectCollabService.requireAtLeast(userId, existing.getProjectId(), ProjectMemberRole.DEVELOPER);
            try {
                ensureProjectWritable(existing.getProjectId());
            } catch (ApiException ignored) {
                continue;
            }
            boolean changed = false;

            if (priority != null && !priority.equals(existing.getPriority())) {
                existing.setPriority(priority);
                changed = true;
            }
            if (Boolean.TRUE.equals(clearAssignee)) {
                if (existing.getAssigneeId() != null || existing.getAssignee() != null) {
                    existing.setAssigneeId(null);
                    existing.setAssignee(null);
                    changed = true;
                }
            } else if (assigneeId != null && !Objects.equals(assigneeId, existing.getAssigneeId())) {
                existing.setAssigneeId(assigneeId);
                existing.setAssignee(null);
                changed = true;
            }

            if (Boolean.TRUE.equals(clearDueTime)) {
                if (existing.getDueTime() != null) {
                    existing.setDueTime(null);
                    existing.setDueRemindedTime(null);
                    changed = true;
                }
            } else if (dueTime != null) {
                LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(dueTime), ZoneId.systemDefault());
                if (existing.getDueTime() == null || !dt.equals(existing.getDueTime())) {
                    existing.setDueTime(dt);
                    existing.setDueRemindedTime(null);
                    changed = true;
                }
            }

            if (!changed) continue;
            if (this.updateById(existing)) {
                ok++;
                timelineService.addEvent(userId, existing.getProjectId(), existing.getId(), TaskTimelineType.UPDATED, "批量更新", existing.getTitle());
                if (projectAuditService != null) {
                    projectAuditService.record(existing.getProjectId(), userId, "TASK_UPDATED", "TASK", existing.getId(), existing.getTitle(), "{\"taskId\":" + existing.getId() + "}");
                }
                realtimeCollabService.broadcast(existing.getProjectId(), userId, "TASK_UPDATED", "{\"taskId\":" + existing.getId() + "}");
                if (existing.getAssigneeId() != null
                        && existing.getAssigneeId() != 0
                        && !Objects.equals(existing.getAssigneeId(), beforeAssigneeId)
                        && !Objects.equals(existing.getAssigneeId(), userId)) {
                    try {
                        if (inboxService != null)
                            inboxService.upsertAssignedTask(existing.getAssigneeId(), existing.getProjectId(), existing.getId(), existing.getTitle());
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return ok;
    }

    @Override
    public List<Task> listByProjectId(Long userId, Long projectId) {
        if (userId == null) {
            throw new IllegalArgumentException("USER_ID_REQUIRED");
        }
        if (projectId == null) {
            throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        }
        projectCollabService.requireMember(userId, projectId);
        return this.list(Wrappers.<Task>lambdaQuery()
                .eq(Task::getProjectId, projectId)
                .orderByDesc(Task::getId));
    }

    @Override
    public List<Long> participatedTaskIds(Long userId, Long projectId) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (projectId == null) throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        projectCollabService.requireMember(userId, projectId);
        return this.baseMapper.participatedTaskIds(userId, projectId);
    }

    @Override
    public Task getDetail(Long userId, Long taskId) {
        if (userId == null) {
            throw new IllegalArgumentException("USER_ID_REQUIRED");
        }
        if (taskId == null) {
            throw new IllegalArgumentException("TASK_ID_REQUIRED");
        }
        Task existing = this.getById(taskId);
        if (existing == null) {
            return null;
        }
        projectCollabService.requireMember(userId, existing.getProjectId());
        return existing;
    }

    @Override
    public List<Task> listSubtasks(Long userId, Long taskId) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (taskId == null) throw new IllegalArgumentException("TASK_ID_REQUIRED");
        Task parent = this.getById(taskId);
        if (parent == null) return new ArrayList<>();
        projectCollabService.requireMember(userId, parent.getProjectId());
        return this.list(Wrappers.<Task>lambdaQuery()
                .eq(Task::getProjectId, parent.getProjectId())
                .eq(Task::getParentTaskId, taskId)
                .orderByDesc(Task::getId));
    }

    @Override
    public boolean updateDetail(Long userId,
                                Long taskId,
                                String title,
                                String description,
                                String acceptanceCriteria,
                                String priority,
                                String tags,
                                String assignee,
                                Long assigneeId,
                                Long dueTime,
                                Long milestoneId,
                                Long parentTaskId,
                                String type,
                                String baseUpdatedAt) {
        if (userId == null) {
            throw new IllegalArgumentException("USER_ID_REQUIRED");
        }
        if (taskId == null) {
            throw new IllegalArgumentException("TASK_ID_REQUIRED");
        }
        Task existing = this.getById(taskId);
        if (existing == null) {
            return false;
        }
        Long beforeAssigneeId = existing.getAssigneeId();
        projectCollabService.requireAtLeast(userId, existing.getProjectId(), ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(existing.getProjectId());
        if (baseUpdatedAt != null && !baseUpdatedAt.isBlank() && existing.getUpdatedAt() != null) {
            LocalDateTime base = parseBaseUpdatedAt(baseUpdatedAt.trim());
            if (base != null && !existing.getUpdatedAt().equals(base)) {
                throw new IllegalArgumentException("TASK_CONFLICT");
            }
        }

        boolean changed = false;
        if (title != null && !title.isBlank() && !title.trim().equals(existing.getTitle())) {
            existing.setTitle(title.trim());
            changed = true;
        }
        if (description != null && !description.equals(existing.getDescription())) {
            existing.setDescription(description);
            changed = true;
        }
        if (acceptanceCriteria != null && !acceptanceCriteria.equals(existing.getAcceptanceCriteria())) {
            existing.setAcceptanceCriteria(acceptanceCriteria);
            changed = true;
        }
        if (priority != null && !priority.equals(existing.getPriority())) {
            existing.setPriority(priority);
            changed = true;
        }
        if (tags != null && !tags.equals(existing.getTags())) {
            existing.setTags(tags);
            changed = true;
        }
        if (assignee != null && !assignee.equals(existing.getAssignee())) {
            existing.setAssignee(assignee);
            changed = true;
        }
        if (assigneeId != null) {
            if (assigneeId == 0) {
                if (existing.getAssigneeId() != null || existing.getAssignee() != null) {
                    existing.setAssigneeId(null);
                    existing.setAssignee(null);
                    changed = true;
                }
            } else if (!assigneeId.equals(existing.getAssigneeId())) {
                existing.setAssigneeId(assigneeId);
                changed = true;
            }
        }
        if (dueTime != null) {
            if (dueTime == 0) {
                if (existing.getDueTime() != null) {
                    existing.setDueTime(null);
                    existing.setDueRemindedTime(null);
                    changed = true;
                }
            } else {
                LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(dueTime), ZoneId.systemDefault());
                if (existing.getDueTime() == null || !dt.equals(existing.getDueTime())) {
                    existing.setDueTime(dt);
                    existing.setDueRemindedTime(null);
                    changed = true;
                }
            }
        }

        if (parentTaskId != null) {
            if (parentTaskId == 0) {
                if (existing.getParentTaskId() != null) {
                    existing.setParentTaskId(null);
                    if ("SUBTASK".equalsIgnoreCase(existing.getType())) existing.setType("TASK");
                    changed = true;
                }
            } else if (!Objects.equals(parentTaskId, existing.getParentTaskId())) {
                if (Objects.equals(parentTaskId, existing.getId())) throw new IllegalArgumentException("PARENT_TASK_INVALID");
                Task parent = this.getById(parentTaskId);
                if (parent == null || !Objects.equals(parent.getProjectId(), existing.getProjectId())) {
                    throw new IllegalArgumentException("PARENT_TASK_INVALID");
                }
                if (parent.getParentTaskId() != null) throw new IllegalArgumentException("PARENT_TASK_INVALID");
                existing.setParentTaskId(parentTaskId);
                existing.setType("SUBTASK");
                if (milestoneId == null || milestoneId == 0) {
                    existing.setMilestoneId(parent.getMilestoneId());
                }
                changed = true;
            }
        }

        if (milestoneId != null) {
            Long mid = milestoneId == 0 ? null : milestoneId;
            if (!Objects.equals(mid, existing.getMilestoneId())) {
                existing.setMilestoneId(mid);
                changed = true;
            }
        }

        if (type != null && existing.getParentTaskId() == null) {
            String t = type.trim().toUpperCase();
            String nt = "EPIC".equals(t) ? "EPIC" : "TASK";
            if (!Objects.equals(nt, existing.getType())) {
                existing.setType(nt);
                changed = true;
            }
        }

        if (!changed) {
            return true;
        }
        boolean ok = this.updateById(existing);
        if (ok) {
            timelineService.addEvent(userId, existing.getProjectId(), existing.getId(), TaskTimelineType.UPDATED, "更新任务", existing.getTitle());
            if (projectAuditService != null) {
                projectAuditService.record(existing.getProjectId(), userId, "TASK_UPDATED", "TASK", existing.getId(), existing.getTitle(), "{\"taskId\":" + existing.getId() + "}");
            }
            realtimeCollabService.broadcast(existing.getProjectId(), userId, "TASK_UPDATED", "{\"taskId\":" + existing.getId() + "}");
            if (existing.getAssigneeId() != null
                    && existing.getAssigneeId() != 0
                    && !Objects.equals(existing.getAssigneeId(), beforeAssigneeId)
                    && !Objects.equals(existing.getAssigneeId(), userId)) {
                try {
                    if (inboxService != null)
                        inboxService.upsertAssignedTask(existing.getAssigneeId(), existing.getProjectId(), existing.getId(), existing.getTitle());
                } catch (Exception ignored) {
                }
            }
        }
        return ok;
    }

    private LocalDateTime parseBaseUpdatedAt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    @Override
    @Transactional
    public boolean deleteTask(Long userId, Long taskId) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (taskId == null) throw new IllegalArgumentException("TASK_ID_REQUIRED");
        Task task = this.getById(taskId);
        if (task == null) return false;
        Long projectId = task.getProjectId();
        projectCollabService.requireAtLeast(userId, projectId, ProjectMemberRole.DEVELOPER);

        if (attachmentMapper != null) {
            List<TaskAttachment> atts = attachmentMapper.selectList(Wrappers.<TaskAttachment>lambdaQuery().eq(TaskAttachment::getTaskId, taskId));
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
            attachmentMapper.delete(Wrappers.<TaskAttachment>lambdaQuery().eq(TaskAttachment::getTaskId, taskId));
        }
        if (deliverableMapper != null) {
            deliverableMapper.delete(Wrappers.<TaskDeliverable>lambdaQuery().eq(TaskDeliverable::getTaskId, taskId));
        }
        if (checklistItemMapper != null) {
            checklistItemMapper.delete(Wrappers.lambdaQuery(com.devtoolcopilot.task.checklist.entity.TaskChecklistItem.class)
                    .eq(com.devtoolcopilot.task.checklist.entity.TaskChecklistItem::getTaskId, taskId));
        }
        if (commentMapper != null) {
            commentMapper.delete(Wrappers.<TaskComment>lambdaQuery().eq(TaskComment::getTaskId, taskId));
        }
        if (followMapper != null) {
            followMapper.delete(Wrappers.<TaskFollow>lambdaQuery().eq(TaskFollow::getTaskId, taskId));
        }
        if (timelineMapper != null) {
            timelineMapper.delete(Wrappers.<TaskTimeline>lambdaQuery().eq(TaskTimeline::getTaskId, taskId));
        }
        if (taskPrLinkMapper != null) {
            taskPrLinkMapper.delete(Wrappers.<TaskPrLink>lambdaQuery().eq(TaskPrLink::getTaskId, taskId));
        }

        boolean ok = this.removeById(taskId);
        if (ok) {
            projectCollabService.addActivity(projectId, userId, "TASK_DELETED", "{\"taskId\":" + taskId + "}");
            if (projectAuditService != null) {
                projectAuditService.record(projectId, userId, "TASK_DELETED", "TASK", taskId, task.getTitle(), "{\"taskId\":" + taskId + "}");
            }
            realtimeCollabService.broadcast(projectId, userId, "TASK_DELETED", "{\"taskId\":" + taskId + "}");
        }
        return ok;
    }

    private void ensureProjectWritable(Long projectId) {
        if (projectId == null) return;
        if (projectMapper == null) return;
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
}
