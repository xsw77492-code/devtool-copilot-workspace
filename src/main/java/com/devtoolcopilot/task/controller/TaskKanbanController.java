package com.devtoolcopilot.task.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.task.dto.TaskKanbanMoveRequest;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.service.TaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/task/kanban")
public class TaskKanbanController {
    private final TaskService taskService;

    public TaskKanbanController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/list")
    public R<List<Task>> list(@RequestParam("projectId") Long projectId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(taskService.kanbanList(userId, projectId));
    }

    @PostMapping("/move")
    public R<Boolean> move(@RequestBody TaskKanbanMoveRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        if (req == null) return R.fail(400, "请求参数错误");
        boolean ok = taskService.kanbanMove(
                userId,
                req.getProjectId(),
                req.getTaskId(),
                req.getToStatus(),
                req.getBeforeId(),
                req.getAfterId(),
                req.getBaseUpdatedAt(),
                req.getForceDone()
        );
        return R.ok(ok);
    }
}
