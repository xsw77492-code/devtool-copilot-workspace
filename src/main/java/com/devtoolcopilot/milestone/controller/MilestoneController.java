package com.devtoolcopilot.milestone.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.milestone.dto.MilestoneCreateRequest;
import com.devtoolcopilot.milestone.dto.MilestonePublishResponse;
import com.devtoolcopilot.milestone.entity.ProjectMilestone;
import com.devtoolcopilot.milestone.service.MilestoneService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/milestone")
public class MilestoneController {
    private final MilestoneService milestoneService;

    public MilestoneController(MilestoneService milestoneService) {
        this.milestoneService = milestoneService;
    }

    @GetMapping("/list")
    public R<List<ProjectMilestone>> list(@RequestParam Long projectId,
                                          @RequestParam(required = false) Boolean includeArchived) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            return R.ok(milestoneService.listByProject(userId, projectId, includeArchived));
        } catch (IllegalArgumentException e) {
            if ("PROJECT_ID_REQUIRED".equals(e.getMessage())) return R.fail(400, "projectId不能为空");
            return R.fail(400, "查询失败");
        }
    }

    @PostMapping("")
    public R<Long> create(@RequestBody MilestoneCreateRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        Long projectId = req == null ? null : req.getProjectId();
        String name = req == null ? null : req.getName();
        String description = req == null ? null : req.getDescription();
        Long dueTime = req == null ? null : req.getDueTime();
        try {
            Long id = milestoneService.create(userId, projectId, name, description, dueTime);
            return R.ok(id);
        } catch (IllegalArgumentException e) {
            if ("PROJECT_ID_REQUIRED".equals(e.getMessage())) return R.fail(400, "projectId不能为空");
            if ("NAME_REQUIRED".equals(e.getMessage())) return R.fail(400, "name不能为空");
            return R.fail(400, "创建失败");
        }
    }

    @PostMapping("/{id}/publish")
    public R<MilestonePublishResponse> publish(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            MilestonePublishResponse res = milestoneService.publish(userId, id);
            if (res == null) return R.fail(404, "里程碑不存在");
            return R.ok(res);
        } catch (IllegalArgumentException e) {
            if ("MILESTONE_ID_REQUIRED".equals(e.getMessage())) return R.fail(400, "id不能为空");
            return R.fail(400, "发布失败");
        }
    }

    @PostMapping("/{id}/archive")
    public R<Void> archive(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            boolean ok = milestoneService.archive(userId, id);
            if (!ok) return R.fail(404, "里程碑不存在");
            return R.ok();
        } catch (IllegalArgumentException e) {
            if ("MILESTONE_ID_REQUIRED".equals(e.getMessage())) return R.fail(400, "id不能为空");
            return R.fail(400, "操作失败");
        }
    }

    @PostMapping("/{id}/unarchive")
    public R<Void> unarchive(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            boolean ok = milestoneService.unarchive(userId, id);
            if (!ok) return R.fail(404, "里程碑不存在");
            return R.ok();
        } catch (IllegalArgumentException e) {
            if ("MILESTONE_ID_REQUIRED".equals(e.getMessage())) return R.fail(400, "id不能为空");
            return R.fail(400, "操作失败");
        }
    }
}

