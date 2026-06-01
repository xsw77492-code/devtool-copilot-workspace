package com.devtoolcopilot.task.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.project.entity.Project;
import com.devtoolcopilot.project.entity.ProjectMemberRole;
import com.devtoolcopilot.project.mapper.ProjectMapper;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.task.comment.dto.TaskCommentCreateRequest;
import com.devtoolcopilot.task.comment.dto.TaskCommentDTO;
import com.devtoolcopilot.task.comment.service.TaskCommentService;
import com.devtoolcopilot.task.dto.TaskBatchStatusRequest;
import com.devtoolcopilot.task.dto.TaskBatchUpdateRequest;
import com.devtoolcopilot.task.dto.TaskCreateRequest;
import com.devtoolcopilot.task.dto.TaskDetailUpdateRequest;
import com.devtoolcopilot.task.dto.TaskNoteCreateRequest;
import com.devtoolcopilot.task.dto.TaskUpdateRequest;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.follow.service.TaskFollowService;
import com.devtoolcopilot.task.service.TaskService;
import com.devtoolcopilot.task.timeline.entity.TaskTimeline;
import com.devtoolcopilot.task.timeline.entity.TaskTimelineType;
import com.devtoolcopilot.task.timeline.service.TaskTimelineService;
import com.devtoolcopilot.task.template.dto.TaskTemplateUpsertRequest;
import com.devtoolcopilot.task.template.entity.TaskTemplate;
import com.devtoolcopilot.task.template.service.TaskTemplateService;
import com.devtoolcopilot.task.view.dto.TaskBoardViewUpsertRequest;
import com.devtoolcopilot.task.view.entity.TaskBoardView;
import com.devtoolcopilot.task.view.service.TaskBoardViewService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/task")
public class TaskController {
    private final TaskService taskService;
    private final TaskTimelineService timelineService;
    private final ProjectCollabService projectCollabService;
    private final ProjectMapper projectMapper;
    private final TaskCommentService taskCommentService;
    private final TaskBoardViewService taskBoardViewService;
    private final TaskTemplateService taskTemplateService;
    private final TaskFollowService taskFollowService;

    public TaskController(TaskService taskService,
                          TaskTimelineService timelineService,
                          ProjectCollabService projectCollabService,
                          ProjectMapper projectMapper,
                          TaskCommentService taskCommentService,
                          TaskBoardViewService taskBoardViewService,
                          TaskTemplateService taskTemplateService,
                          TaskFollowService taskFollowService) {
        this.taskService = taskService;
        this.timelineService = timelineService;
        this.projectCollabService = projectCollabService;
        this.projectMapper = projectMapper;
        this.taskCommentService = taskCommentService;
        this.taskBoardViewService = taskBoardViewService;
        this.taskTemplateService = taskTemplateService;
        this.taskFollowService = taskFollowService;
    }

    @PostMapping
    public R<Long> create(@RequestBody TaskCreateRequest req) {
        return createInternal(req);
    }

    @PostMapping("/create")
    public R<Long> create2(@RequestBody TaskCreateRequest req) {
        return createInternal(req);
    }

    private R<Long> createInternal(TaskCreateRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        try {
            return R.ok(taskService.createTask(
                    userId,
                    req.getProjectId(),
                    req.getTitle(),
                    req.getDescription(),
                    req.getAcceptanceCriteria(),
                    req.getPriority(),
                    req.getTags(),
                    req.getAssignee(),
                    req.getAssigneeId(),
                    req.getDueTime(),
                    req.getMilestoneId(),
                    req.getParentTaskId(),
                    req.getType(),
                    req.getSource()
            ));
        } catch (IllegalArgumentException e) {
            if ("PROJECT_ID_REQUIRED".equals(e.getMessage())) {
                return R.fail(400, "projectId不能为空");
            }
            if ("TITLE_REQUIRED".equals(e.getMessage())) {
                return R.fail(400, "title不能为空");
            }
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) {
                return R.fail(403, "无权限或项目不存在");
            }
            if ("PARENT_TASK_INVALID".equals(e.getMessage())) {
                return R.fail(400, "父任务无效");
            }
            return R.fail(400, "创建任务失败");
        }
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody TaskUpdateRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        try {
            boolean ok = taskService.updateStatus(userId, id, req.getStatus(), req == null ? null : req.getBaseUpdatedAt(), req == null ? null : req.getForceDone());
            if (!ok) {
                return R.fail(404, "任务不存在");
            }
            return R.ok();
        } catch (IllegalArgumentException e) {
            if ("TASK_CONFLICT".equals(e.getMessage())) {
                return R.fail(409, "任务已被其他人更新，请刷新后再试");
            }
            if ("STATUS_REQUIRED".equals(e.getMessage())) {
                return R.fail(400, "status不能为空");
            }
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) {
                return R.fail(403, "无权限或项目不存在");
            }
            return R.fail(400, "更新状态失败");
        }
    }

    @GetMapping("/list")
    public R<List<Task>> list(@RequestParam Long projectId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        try {
            return R.ok(taskService.listByProjectId(userId, projectId));
        } catch (IllegalArgumentException e) {
            if ("PROJECT_ID_REQUIRED".equals(e.getMessage())) {
                return R.fail(400, "projectId不能为空");
            }
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) {
                return R.fail(403, "无权限或项目不存在");
            }
            return R.fail(400, "查询任务失败");
        }
    }

    @GetMapping("/{id}")
    public R<Task> detail(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        try {
            Task task = taskService.getDetail(userId, id);
            if (task == null) return R.fail(404, "任务不存在");
            return R.ok(task);
        } catch (IllegalArgumentException e) {
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) {
                return R.fail(403, "无权限或项目不存在");
            }
            return R.fail(400, "查询失败");
        }
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteTask(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        try {
            boolean ok = taskService.deleteTask(userId, id);
            if (!ok) return R.fail(404, "任务不存在");
            return R.ok();
        } catch (IllegalArgumentException e) {
            if ("TASK_ID_REQUIRED".equals(e.getMessage())) return R.fail(400, "id不能为空");
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) return R.fail(403, "无权限或项目不存在");
            return R.fail(400, "删除失败");
        }
    }

    @PutMapping("/{id}")
    public R<Void> updateDetail(@PathVariable Long id, @RequestBody TaskDetailUpdateRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        try {
            boolean ok = taskService.updateDetail(
                    userId,
                    id,
                    req.getTitle(),
                    req.getDescription(),
                    req.getAcceptanceCriteria(),
                    req.getPriority(),
                    req.getTags(),
                    req.getAssignee(),
                    req.getAssigneeId(),
                    req.getDueTime(),
                    req.getMilestoneId(),
                    req.getParentTaskId(),
                    req.getType(),
                    req.getBaseUpdatedAt()
            );
            if (!ok) return R.fail(404, "任务不存在");
            return R.ok();
        } catch (IllegalArgumentException e) {
            if ("TASK_CONFLICT".equals(e.getMessage())) {
                return R.fail(409, "任务已被其他人更新，请刷新后再试");
            }
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) {
                return R.fail(403, "无权限或项目不存在");
            }
            if ("PARENT_TASK_INVALID".equals(e.getMessage())) {
                return R.fail(400, "父任务无效");
            }
            return R.fail(400, "更新失败");
        }
    }

    @GetMapping("/{id}/subtasks")
    public R<List<Task>> subtasks(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            return R.ok(taskService.listSubtasks(userId, id));
        } catch (IllegalArgumentException e) {
            return R.fail(400, "查询失败");
        }
    }

    @GetMapping("/{id}/timeline")
    public R<List<TaskTimeline>> timeline(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        try {
            Task task = taskService.getDetail(userId, id);
            if (task == null) return R.fail(404, "任务不存在");
            return R.ok(timelineService.listByTask(userId, task.getProjectId(), id));
        } catch (IllegalArgumentException e) {
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) {
                return R.fail(403, "无权限或项目不存在");
            }
            return R.fail(400, "查询失败");
        }
    }

    @PostMapping("/{id}/note")
    public R<Long> addNote(@PathVariable Long id, @RequestBody TaskNoteCreateRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        String content = req == null ? null : req.getContent();
        if (content == null || content.isBlank()) {
            return R.fail(400, "content不能为空");
        }
        try {
            Task task = taskService.getDetail(userId, id);
            if (task == null) return R.fail(404, "任务不存在");
            projectCollabService.requireAtLeast(userId, task.getProjectId(), ProjectMemberRole.DEVELOPER);
            Project p = projectMapper.selectById(task.getProjectId());
            if (p != null && p.getArchived() != null && p.getArchived() == 1) {
                return R.fail(400, "项目已归档");
            }
            Long eid = timelineService.addEvent(userId, task.getProjectId(), id, TaskTimelineType.NOTE, "备注", content.trim());
            return R.ok(eid);
        } catch (IllegalArgumentException e) {
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) {
                return R.fail(403, "无权限或项目不存在");
            }
            return R.fail(400, "添加失败");
        }
    }

    @GetMapping("/{id}/followed")
    public R<Boolean> isFollowed(@PathVariable Long id, @RequestParam Long projectId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            projectCollabService.requireMember(userId, projectId);
            return R.ok(taskFollowService.isFollowing(userId, id));
        } catch (IllegalArgumentException e) {
            return R.fail(403, "无权限或项目不存在");
        }
    }

    @PostMapping("/{id}/follow")
    public R<Void> follow(@PathVariable Long id, @RequestParam Long projectId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            taskFollowService.follow(userId, projectId, id);
            return R.ok();
        } catch (IllegalArgumentException e) {
            if ("PROJECT_ID_REQUIRED".equals(e.getMessage())) return R.fail(400, "projectId不能为空");
            return R.fail(400, "操作失败");
        }
    }

    @PostMapping("/{id}/unfollow")
    public R<Void> unfollow(@PathVariable Long id, @RequestParam Long projectId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            taskFollowService.unfollow(userId, projectId, id);
            return R.ok();
        } catch (IllegalArgumentException e) {
            if ("PROJECT_ID_REQUIRED".equals(e.getMessage())) return R.fail(400, "projectId不能为空");
            return R.fail(400, "操作失败");
        }
    }

    @GetMapping("/{id}/comments")
    public R<List<TaskCommentDTO>> comments(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        try {
            Task task = taskService.getDetail(userId, id);
            if (task == null) return R.fail(404, "任务不存在");
            return R.ok(taskCommentService.listByTask(userId, id));
        } catch (IllegalArgumentException e) {
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) {
                return R.fail(403, "无权限或项目不存在");
            }
            return R.fail(400, "查询失败");
        }
    }

    @PostMapping("/{id}/comment")
    public R<Long> addComment(@PathVariable Long id, @RequestBody TaskCommentCreateRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        String content = req == null ? null : req.getContent();
        if (content == null || content.isBlank()) {
            return R.fail(400, "content不能为空");
        }
        try {
            Long cid = taskCommentService.addComment(userId, id, content, req.getReplyToId());
            return R.ok(cid);
        } catch (IllegalArgumentException e) {
            if ("TASK_NOT_FOUND".equals(e.getMessage())) {
                return R.fail(404, "任务不存在");
            }
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) {
                return R.fail(403, "无权限或项目不存在");
            }
            return R.fail(400, "添加失败");
        }
    }

    @PutMapping("/batch/status")
    public R<Integer> batchStatus(@RequestBody TaskBatchStatusRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            int ok = taskService.batchUpdateStatus(userId, req == null ? null : req.getTaskIds(), req == null ? null : req.getStatus(), req == null ? null : req.getForceDone());
            return R.ok(ok);
        } catch (IllegalArgumentException e) {
            if ("TASK_IDS_REQUIRED".equals(e.getMessage())) return R.fail(400, "taskIds不能为空");
            if ("STATUS_REQUIRED".equals(e.getMessage())) return R.fail(400, "status不能为空");
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) return R.fail(403, "无权限或项目不存在");
            return R.fail(400, "批量更新失败");
        }
    }

    @PutMapping("/batch/update")
    public R<Integer> batchUpdate(@RequestBody TaskBatchUpdateRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            int ok = taskService.batchUpdateFields(
                    userId,
                    req == null ? null : req.getTaskIds(),
                    req == null ? null : req.getPriority(),
                    req == null ? null : req.getAssigneeId(),
                    req == null ? null : req.getDueTime(),
                    req == null ? null : req.getClearAssignee(),
                    req == null ? null : req.getClearDueTime()
            );
            return R.ok(ok);
        } catch (IllegalArgumentException e) {
            if ("TASK_IDS_REQUIRED".equals(e.getMessage())) return R.fail(400, "taskIds不能为空");
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) return R.fail(403, "无权限或项目不存在");
            return R.fail(400, "批量更新失败");
        }
    }

    @GetMapping("/participated-ids")
    public R<List<Long>> participatedIds(@RequestParam Long projectId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            return R.ok(taskService.participatedTaskIds(userId, projectId));
        } catch (IllegalArgumentException e) {
            if ("PROJECT_ID_REQUIRED".equals(e.getMessage())) return R.fail(400, "projectId不能为空");
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) return R.fail(403, "无权限或项目不存在");
            return R.fail(400, "查询失败");
        }
    }

    @GetMapping("/views")
    public R<List<TaskBoardView>> views(@RequestParam Long projectId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            return R.ok(taskBoardViewService.list(userId, projectId));
        } catch (IllegalArgumentException e) {
            if ("PROJECT_ID_REQUIRED".equals(e.getMessage())) return R.fail(400, "projectId不能为空");
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) return R.fail(403, "无权限或项目不存在");
            return R.fail(400, "查询失败");
        }
    }

    @PostMapping("/view")
    public R<TaskBoardView> createView(@RequestBody TaskBoardViewUpsertRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            return R.ok(taskBoardViewService.create(
                    userId,
                    req == null ? null : req.getProjectId(),
                    req == null ? null : req.getName(),
                    req == null ? null : req.getColor(),
                    req == null ? null : req.getFiltersJson()
            ));
        } catch (IllegalArgumentException e) {
            if ("PROJECT_ID_REQUIRED".equals(e.getMessage())) return R.fail(400, "projectId不能为空");
            if ("NAME_REQUIRED".equals(e.getMessage())) return R.fail(400, "name不能为空");
            if ("FILTERS_REQUIRED".equals(e.getMessage())) return R.fail(400, "filtersJson不能为空");
            if ("DUP_NAME".equals(e.getMessage())) return R.fail(409, "视图名称已存在");
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) return R.fail(403, "无权限或项目不存在");
            return R.fail(400, "创建失败");
        }
    }

    @PutMapping("/view/{id}")
    public R<TaskBoardView> updateView(@PathVariable Long id, @RequestBody TaskBoardViewUpsertRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            TaskBoardView v = taskBoardViewService.update(
                    userId,
                    id,
                    req == null ? null : req.getName(),
                    req == null ? null : req.getColor(),
                    req == null ? null : req.getFiltersJson()
            );
            if (v == null) return R.fail(404, "视图不存在");
            return R.ok(v);
        } catch (IllegalArgumentException e) {
            if ("FORBIDDEN".equals(e.getMessage())) return R.fail(403, "无权限");
            if ("DUP_NAME".equals(e.getMessage())) return R.fail(409, "视图名称已存在");
            return R.fail(400, "更新失败");
        }
    }

    @DeleteMapping("/view/{id}")
    public R<Void> deleteView(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            taskBoardViewService.delete(userId, id);
            return R.ok();
        } catch (IllegalArgumentException e) {
            if ("FORBIDDEN".equals(e.getMessage())) return R.fail(403, "无权限");
            return R.fail(400, "删除失败");
        }
    }

    @GetMapping("/templates")
    public R<List<TaskTemplate>> templates(@RequestParam(required = false) Long projectId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            return R.ok(taskTemplateService.list(userId, projectId));
        } catch (IllegalArgumentException e) {
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) return R.fail(403, "无权限或项目不存在");
            return R.fail(400, "查询失败");
        }
    }

    @PostMapping("/template")
    public R<TaskTemplate> createTemplate(@RequestBody TaskTemplateUpsertRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            return R.ok(taskTemplateService.create(userId, req == null ? null : req.getProjectId(), req == null ? null : req.getName(), req == null ? null : req.getPayloadJson()));
        } catch (IllegalArgumentException e) {
            if ("NAME_REQUIRED".equals(e.getMessage())) return R.fail(400, "name不能为空");
            if ("PAYLOAD_REQUIRED".equals(e.getMessage())) return R.fail(400, "payloadJson不能为空");
            if ("DUP_NAME".equals(e.getMessage())) return R.fail(409, "模板名称已存在");
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) return R.fail(403, "无权限或项目不存在");
            return R.fail(400, "创建失败");
        }
    }

    @PutMapping("/template/{id}")
    public R<TaskTemplate> updateTemplate(@PathVariable Long id, @RequestBody TaskTemplateUpsertRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            TaskTemplate t = taskTemplateService.update(userId, id, req == null ? null : req.getName(), req == null ? null : req.getPayloadJson());
            if (t == null) return R.fail(404, "模板不存在");
            return R.ok(t);
        } catch (IllegalArgumentException e) {
            if ("FORBIDDEN".equals(e.getMessage())) return R.fail(403, "无权限");
            if ("DUP_NAME".equals(e.getMessage())) return R.fail(409, "模板名称已存在");
            return R.fail(400, "更新失败");
        }
    }

    @DeleteMapping("/template/{id}")
    public R<Void> deleteTemplate(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            taskTemplateService.delete(userId, id);
            return R.ok();
        } catch (IllegalArgumentException e) {
            if ("FORBIDDEN".equals(e.getMessage())) return R.fail(403, "无权限");
            return R.fail(400, "删除失败");
        }
    }
}
