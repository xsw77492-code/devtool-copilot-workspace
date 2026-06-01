package com.devtoolcopilot.task.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.task.deliverable.dto.TaskDeliverableCreateRequest;
import com.devtoolcopilot.task.deliverable.dto.TaskDeliverableItem;
import com.devtoolcopilot.task.deliverable.dto.TaskDeliverableMoveRequest;
import com.devtoolcopilot.task.deliverable.dto.TaskDeliverableUpdateRequest;
import com.devtoolcopilot.task.deliverable.service.TaskDeliverableService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/task")
public class TaskDeliverableController {
    private final TaskDeliverableService taskDeliverableService;

    public TaskDeliverableController(TaskDeliverableService taskDeliverableService) {
        this.taskDeliverableService = taskDeliverableService;
    }

    @GetMapping("/{taskId}/deliverables")
    public R<List<TaskDeliverableItem>> list(@PathVariable Long taskId) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        return R.ok(taskDeliverableService.listByTask(me, taskId));
    }

    @PostMapping("/{taskId}/deliverable")
    public R<Long> create(@PathVariable Long taskId, @RequestBody TaskDeliverableCreateRequest req) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        if (req == null) return R.fail(400, "请求参数错误");
        return R.ok(taskDeliverableService.create(me, taskId, req.getType(), req.getTitle(), req.getUrl(), req.getContent()));
    }

    @PutMapping("/deliverable/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody TaskDeliverableUpdateRequest req) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        if (req == null) return R.fail(400, "请求参数错误");
        boolean ok = taskDeliverableService.update(me, id, req.getTitle(), req.getUrl(), req.getContent(), req.getStatus(), req.getSort());
        if (!ok) return R.fail(404, "交付物不存在");
        return R.ok();
    }

    @PostMapping("/deliverable/{id}/move")
    public R<Void> move(@PathVariable Long id, @RequestBody TaskDeliverableMoveRequest req) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        if (req == null) return R.fail(400, "请求参数错误");
        boolean ok = taskDeliverableService.move(me, id, req.getBeforeId(), req.getAfterId());
        if (!ok) return R.fail(404, "交付物不存在");
        return R.ok();
    }

    @DeleteMapping("/deliverable/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        boolean ok = taskDeliverableService.delete(me, id);
        if (!ok) return R.fail(404, "交付物不存在");
        return R.ok();
    }
}
