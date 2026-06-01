package com.devtoolcopilot.task.checklist.service.impl;

import com.devtoolcopilot.audit.service.ProjectAuditService;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.project.entity.Project;
import com.devtoolcopilot.project.entity.ProjectMemberRole;
import com.devtoolcopilot.project.mapper.ProjectMapper;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.realtime.service.RealtimeCollabService;
import com.devtoolcopilot.task.checklist.dto.TaskChecklistItemDTO;
import com.devtoolcopilot.task.checklist.entity.TaskChecklistItem;
import com.devtoolcopilot.task.checklist.mapper.TaskChecklistItemMapper;
import com.devtoolcopilot.task.checklist.service.TaskChecklistService;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.service.TaskService;
import com.devtoolcopilot.task.timeline.entity.TaskTimelineType;
import com.devtoolcopilot.task.timeline.service.TaskTimelineService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class TaskChecklistServiceImpl implements TaskChecklistService {
    private final TaskChecklistItemMapper checklistItemMapper;
    private final TaskService taskService;
    private final ProjectCollabService projectCollabService;
    private final ProjectMapper projectMapper;
    private final TaskTimelineService timelineService;
    private final RealtimeCollabService realtimeCollabService;
    private final ProjectAuditService projectAuditService;

    public TaskChecklistServiceImpl(TaskChecklistItemMapper checklistItemMapper,
                                    TaskService taskService,
                                    ProjectCollabService projectCollabService,
                                    ProjectMapper projectMapper,
                                    TaskTimelineService timelineService,
                                    RealtimeCollabService realtimeCollabService,
                                    ProjectAuditService projectAuditService) {
        this.checklistItemMapper = checklistItemMapper;
        this.taskService = taskService;
        this.projectCollabService = projectCollabService;
        this.projectMapper = projectMapper;
        this.timelineService = timelineService;
        this.realtimeCollabService = realtimeCollabService;
        this.projectAuditService = projectAuditService;
    }

    @Override
    public List<TaskChecklistItemDTO> listByTask(Long userId, Long taskId) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (taskId == null) throw new ApiException(400, "taskId不能为空");
        Task task = taskService.getDetail(userId, taskId);
        if (task == null) throw new ApiException(404, "任务不存在");
        return checklistItemMapper.listByTask(taskId);
    }

    @Override
    public Long create(Long userId, Long taskId, String content) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (taskId == null) throw new ApiException(400, "taskId不能为空");
        if (content == null || content.isBlank()) throw new ApiException(400, "content不能为空");

        Task task = taskService.getDetail(userId, taskId);
        if (task == null) throw new ApiException(404, "任务不存在");
        projectCollabService.requireAtLeast(userId, task.getProjectId(), ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(task.getProjectId());

        TaskChecklistItem row = new TaskChecklistItem();
        row.setProjectId(task.getProjectId());
        row.setTaskId(taskId);
        row.setUserId(userId);
        row.setContent(content.trim());
        row.setIsDone(0);
        checklistItemMapper.insert(row);

        String detail = "{\"checklistItemId\":" + row.getId() + "}";
        timelineService.addEvent(userId, task.getProjectId(), taskId, TaskTimelineType.UPDATED, "验收清单", "新增 · " + row.getContent());
        realtimeCollabService.broadcast(task.getProjectId(), userId, "TASK_CHECKLIST_CREATED", detail);
        if (projectAuditService != null) {
            projectAuditService.record(task.getProjectId(), userId, "TASK_CHECKLIST_CREATED", "TASK", taskId, task.getTitle(), detail);
        }
        return row.getId();
    }

    @Override
    public boolean update(Long userId, Long id, String content, Boolean done) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (id == null) throw new ApiException(400, "id不能为空");
        TaskChecklistItem row = checklistItemMapper.selectById(id);
        if (row == null) return false;

        Task task = taskService.getDetail(userId, row.getTaskId());
        if (task == null) throw new ApiException(404, "任务不存在");
        projectCollabService.requireAtLeast(userId, task.getProjectId(), ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(task.getProjectId());

        boolean changed = false;
        if (content != null && !content.isBlank() && !content.trim().equals(row.getContent())) {
            row.setContent(content.trim());
            changed = true;
        }
        if (done != null) {
            int t = Boolean.TRUE.equals(done) ? 1 : 0;
            if (!Objects.equals(row.getIsDone(), t)) {
                row.setIsDone(t);
                row.setDoneTime(t == 1 ? LocalDateTime.now() : null);
                changed = true;
            }
        }
        if (!changed) return true;

        int n = checklistItemMapper.updateById(row);
        if (n <= 0) return false;

        String detail = "{\"checklistItemId\":" + row.getId() + ",\"done\":" + (row.getIsDone() == null ? 0 : row.getIsDone()) + "}";
        timelineService.addEvent(userId, task.getProjectId(), row.getTaskId(), TaskTimelineType.UPDATED, "验收清单", "更新 · " + row.getContent());
        realtimeCollabService.broadcast(task.getProjectId(), userId, "TASK_CHECKLIST_UPDATED", detail);
        if (projectAuditService != null) {
            projectAuditService.record(task.getProjectId(), userId, "TASK_CHECKLIST_UPDATED", "TASK", row.getTaskId(), task.getTitle(), detail);
        }
        return true;
    }

    @Override
    public boolean delete(Long userId, Long id) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (id == null) throw new ApiException(400, "id不能为空");
        TaskChecklistItem row = checklistItemMapper.selectById(id);
        if (row == null) return false;
        Task task = taskService.getDetail(userId, row.getTaskId());
        if (task == null) throw new ApiException(404, "任务不存在");
        projectCollabService.requireAtLeast(userId, task.getProjectId(), ProjectMemberRole.DEVELOPER);
        ensureProjectWritable(task.getProjectId());

        int n = checklistItemMapper.deleteById(id);
        if (n <= 0) return false;

        String detail = "{\"checklistItemId\":" + id + "}";
        timelineService.addEvent(userId, task.getProjectId(), row.getTaskId(), TaskTimelineType.UPDATED, "验收清单", "删除 · " + row.getContent());
        realtimeCollabService.broadcast(task.getProjectId(), userId, "TASK_CHECKLIST_DELETED", detail);
        if (projectAuditService != null) {
            projectAuditService.record(task.getProjectId(), userId, "TASK_CHECKLIST_DELETED", "TASK", row.getTaskId(), task.getTitle(), detail);
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
}
