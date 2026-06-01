package com.devtoolcopilot.task.deliverable.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.audit.service.ProjectAuditService;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.project.entity.Project;
import com.devtoolcopilot.project.entity.ProjectMemberRole;
import com.devtoolcopilot.project.mapper.ProjectMapper;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.realtime.service.RealtimeCollabService;
import com.devtoolcopilot.task.deliverable.dto.TaskDeliverableItem;
import com.devtoolcopilot.task.deliverable.entity.TaskDeliverable;
import com.devtoolcopilot.task.deliverable.mapper.TaskDeliverableMapper;
import com.devtoolcopilot.task.deliverable.service.TaskDeliverableService;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.service.TaskService;
import com.devtoolcopilot.task.timeline.entity.TaskTimelineType;
import com.devtoolcopilot.task.timeline.service.TaskTimelineService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class TaskDeliverableServiceImpl implements TaskDeliverableService {
    private final TaskDeliverableMapper deliverableMapper;
    private final TaskService taskService;
    private final ProjectCollabService projectCollabService;
    private final TaskTimelineService timelineService;
    private final RealtimeCollabService realtimeCollabService;
    private final ProjectAuditService projectAuditService;
    private final ProjectMapper projectMapper;

    public TaskDeliverableServiceImpl(TaskDeliverableMapper deliverableMapper,
                                      TaskService taskService,
                                      ProjectCollabService projectCollabService,
                                      TaskTimelineService timelineService,
                                      RealtimeCollabService realtimeCollabService,
                                      ProjectAuditService projectAuditService,
                                      ProjectMapper projectMapper) {
        this.deliverableMapper = deliverableMapper;
        this.taskService = taskService;
        this.projectCollabService = projectCollabService;
        this.timelineService = timelineService;
        this.realtimeCollabService = realtimeCollabService;
        this.projectAuditService = projectAuditService;
        this.projectMapper = projectMapper;
    }

    @Override
    public List<TaskDeliverableItem> listByTask(Long userId, Long taskId) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (taskId == null) throw new ApiException(400, "taskId不能为空");
        Task task = taskService.getDetail(userId, taskId);
        if (task == null) throw new ApiException(404, "任务不存在");
        return deliverableMapper.listByTask(taskId);
    }

    @Override
    public Long create(Long userId, Long taskId, String type, String title, String url, String content) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (taskId == null) throw new ApiException(400, "taskId不能为空");
        if (title == null || title.isBlank()) throw new ApiException(400, "title不能为空");
        String t = type == null ? "LINK" : type.trim().toUpperCase();
        if (!List.of("LINK", "DOC", "PR").contains(t)) throw new ApiException(400, "type不合法");

        Task task = taskService.getDetail(userId, taskId);
        if (task == null) throw new ApiException(404, "任务不存在");
        projectCollabService.requireAtLeast(userId, task.getProjectId(), ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(task.getProjectId());

        TaskDeliverable d = new TaskDeliverable();
        d.setProjectId(task.getProjectId());
        d.setTaskId(taskId);
        d.setUserId(userId);
        d.setType(t);
        d.setTitle(title.trim());
        d.setUrl(url == null ? null : url.trim());
        d.setContent(content);
        d.setStatus("PENDING");
        Long max = deliverableMapper.maxSortKey(taskId);
        d.setSort((max == null ? 1000L : (max + 1000L)));
        deliverableMapper.insert(d);

        String detail = "{\"deliverableId\":" + d.getId() + ",\"type\":\"" + t + "\"}";
        timelineService.addEvent(userId, task.getProjectId(), taskId, TaskTimelineType.UPDATED, "交付物", "新增 · " + t + " · " + d.getTitle());
        realtimeCollabService.broadcast(task.getProjectId(), userId, "TASK_DELIVERABLE_CREATED", detail);
        if (projectAuditService != null) {
            projectAuditService.record(task.getProjectId(), userId, "TASK_DELIVERABLE_CREATED", "TASK", taskId, task.getTitle(), detail);
        }
        return d.getId();
    }

    @Override
    public boolean update(Long userId, Long deliverableId, String title, String url, String content, String status, Long sort) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (deliverableId == null) throw new ApiException(400, "id不能为空");
        TaskDeliverable d = deliverableMapper.selectById(deliverableId);
        if (d == null) return false;

        Task task = taskService.getDetail(userId, d.getTaskId());
        if (task == null) throw new ApiException(404, "任务不存在");
        projectCollabService.requireAtLeast(userId, task.getProjectId(), ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(task.getProjectId());

        boolean changed = false;
        if (title != null && !title.isBlank() && !title.trim().equals(d.getTitle())) {
            d.setTitle(title.trim());
            changed = true;
        }
        if (url != null && !Objects.equals(url.trim(), d.getUrl())) {
            d.setUrl(url.trim());
            changed = true;
        }
        if (content != null && !Objects.equals(content, d.getContent())) {
            d.setContent(content);
            changed = true;
        }
        if (status != null && !status.isBlank()) {
            String s = status.trim().toUpperCase();
            if (List.of("PENDING", "DONE").contains(s) && !s.equals(d.getStatus())) {
                d.setStatus(s);
                changed = true;
            }
        }
        if (sort != null && !Objects.equals(sort, d.getSort())) {
            d.setSort(sort);
            changed = true;
        }
        if (!changed) return true;

        int n = deliverableMapper.updateById(d);
        if (n <= 0) return false;

        String detail = "{\"deliverableId\":" + d.getId() + ",\"status\":\"" + (d.getStatus() == null ? "" : d.getStatus()) + "\"}";
        timelineService.addEvent(userId, task.getProjectId(), d.getTaskId(), TaskTimelineType.UPDATED, "交付物", "更新 · " + d.getType() + " · " + d.getTitle());
        realtimeCollabService.broadcast(task.getProjectId(), userId, "TASK_DELIVERABLE_UPDATED", detail);
        if (projectAuditService != null) {
            projectAuditService.record(task.getProjectId(), userId, "TASK_DELIVERABLE_UPDATED", "TASK", d.getTaskId(), task.getTitle(), detail);
        }
        return true;
    }

    @Override
    public boolean move(Long userId, Long deliverableId, Long beforeId, Long afterId) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (deliverableId == null) throw new ApiException(400, "id不能为空");
        TaskDeliverable d = deliverableMapper.selectById(deliverableId);
        if (d == null) return false;

        Task task = taskService.getDetail(userId, d.getTaskId());
        if (task == null) throw new ApiException(404, "任务不存在");
        projectCollabService.requireAtLeast(userId, task.getProjectId(), ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(task.getProjectId());

        Long beforeSort = resolveDeliverableRefSort(d.getTaskId(), beforeId, deliverableId);
        Long afterSort = resolveDeliverableRefSort(d.getTaskId(), afterId, deliverableId);

        long newSort = computeDeliverableSort(d.getTaskId(), beforeSort, afterSort, deliverableId);
        if (beforeSort != null && afterSort != null && Math.abs((beforeSort - afterSort)) <= 1) {
            reindexDeliverables(d.getTaskId());
            beforeSort = resolveDeliverableRefSort(d.getTaskId(), beforeId, deliverableId);
            afterSort = resolveDeliverableRefSort(d.getTaskId(), afterId, deliverableId);
            newSort = computeDeliverableSort(d.getTaskId(), beforeSort, afterSort, deliverableId);
        }

        if (Objects.equals(d.getSort(), newSort)) return true;
        d.setSort(newSort);
        int n = deliverableMapper.updateById(d);
        if (n <= 0) return false;

        String detail = "{\"deliverableId\":" + d.getId() + ",\"beforeId\":" + (beforeId == null ? "null" : beforeId) + ",\"afterId\":" + (afterId == null ? "null" : afterId) + "}";
        timelineService.addEvent(userId, task.getProjectId(), d.getTaskId(), TaskTimelineType.UPDATED, "交付物", "排序 · " + d.getType() + " · " + d.getTitle());
        realtimeCollabService.broadcast(task.getProjectId(), userId, "TASK_DELIVERABLE_MOVED", detail);
        if (projectAuditService != null) {
            projectAuditService.record(task.getProjectId(), userId, "TASK_DELIVERABLE_MOVED", "TASK", d.getTaskId(), task.getTitle(), detail);
        }
        return true;
    }

    @Override
    public boolean delete(Long userId, Long deliverableId) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (deliverableId == null) throw new ApiException(400, "id不能为空");
        TaskDeliverable d = deliverableMapper.selectById(deliverableId);
        if (d == null) return false;
        Task task = taskService.getDetail(userId, d.getTaskId());
        if (task == null) throw new ApiException(404, "任务不存在");
        projectCollabService.requireAtLeast(userId, task.getProjectId(), ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(task.getProjectId());

        int n = deliverableMapper.deleteById(deliverableId);
        if (n <= 0) return false;

        String detail = "{\"deliverableId\":" + deliverableId + "}";
        timelineService.addEvent(userId, task.getProjectId(), d.getTaskId(), TaskTimelineType.UPDATED, "交付物", "删除 · " + d.getType() + " · " + d.getTitle());
        realtimeCollabService.broadcast(task.getProjectId(), userId, "TASK_DELIVERABLE_DELETED", detail);
        if (projectAuditService != null) {
            projectAuditService.record(task.getProjectId(), userId, "TASK_DELIVERABLE_DELETED", "TASK", d.getTaskId(), task.getTitle(), detail);
        }
        return true;
    }

    private Long resolveDeliverableRefSort(Long taskId, Long refDeliverableId, Long movingDeliverableId) {
        if (refDeliverableId == null) return null;
        if (movingDeliverableId != null && movingDeliverableId.equals(refDeliverableId)) return null;
        TaskDeliverable ref = deliverableMapper.selectById(refDeliverableId);
        if (ref == null) return null;
        if (!Objects.equals(ref.getTaskId(), taskId)) return null;
        if (ref.getId() == null) return null;
        if (ref.getSort() == null || ref.getSort() == 0) return ref.getId();
        return ref.getSort();
    }

    private long computeDeliverableSort(Long taskId, Long beforeSort, Long afterSort, Long movingDeliverableId) {
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
        Long max = deliverableMapper.maxSortKey(taskId);
        return (max == null ? 1000L : (max + 1000L));
    }

    private void reindexDeliverables(Long taskId) {
        List<TaskDeliverable> list = deliverableMapper.selectList(Wrappers.<TaskDeliverable>lambdaQuery()
                .eq(TaskDeliverable::getTaskId, taskId)
                .last("ORDER BY COALESCE(sort, id) DESC, id DESC"));
        if (list == null || list.isEmpty()) return;
        long start = (long) list.size() * 1000L;
        for (int i = 0; i < list.size(); i++) {
            TaskDeliverable row = list.get(i);
            if (row == null) continue;
            row.setSort(start - (long) i * 1000L);
            deliverableMapper.updateById(row);
        }
    }

    private void ensureProjectWritable(Long projectId) {
        if (projectId == null) return;
        if (projectMapper == null) return;
        Project p = projectMapper.selectById(projectId);
        if (p != null && p.getArchived() != null && p.getArchived() == 1) {
            throw new ApiException(400, "项目已归档");
        }
    }
}
