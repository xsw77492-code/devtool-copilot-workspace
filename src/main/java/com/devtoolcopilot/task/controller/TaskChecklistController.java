package com.devtoolcopilot.task.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.task.checklist.dto.TaskChecklistCreateRequest;
import com.devtoolcopilot.task.checklist.dto.TaskChecklistItemDTO;
import com.devtoolcopilot.task.checklist.dto.TaskChecklistUpdateRequest;
import com.devtoolcopilot.task.checklist.service.TaskChecklistService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/task")
public class TaskChecklistController {
    private final TaskChecklistService taskChecklistService;

    public TaskChecklistController(TaskChecklistService taskChecklistService) {
        this.taskChecklistService = taskChecklistService;
    }

    @GetMapping("/{taskId}/checklist")
    public R<List<TaskChecklistItemDTO>> list(@PathVariable Long taskId) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        return R.ok(taskChecklistService.listByTask(me, taskId));
    }

    @PostMapping("/{taskId}/checklist")
    public R<Long> create(@PathVariable Long taskId, @RequestBody TaskChecklistCreateRequest req) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        if (req == null) return R.fail(400, "请求参数错误");
        return R.ok(taskChecklistService.create(me, taskId, req.getContent()));
    }

    @PutMapping("/checklist/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody TaskChecklistUpdateRequest req) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        if (req == null) return R.fail(400, "请求参数错误");
        boolean ok = taskChecklistService.update(me, id, req.getContent(), req.getDone());
        if (!ok) return R.fail(404, "清单不存在");
        return R.ok();
    }

    @DeleteMapping("/checklist/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        boolean ok = taskChecklistService.delete(me, id);
        if (!ok) return R.fail(404, "清单不存在");
        return R.ok();
    }
}

