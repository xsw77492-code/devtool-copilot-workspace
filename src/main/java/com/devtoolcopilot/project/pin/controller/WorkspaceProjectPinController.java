package com.devtoolcopilot.project.pin.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.project.pin.dto.UserProjectPinUpsertRequest;
import com.devtoolcopilot.project.pin.service.UserProjectPinService;
import com.devtoolcopilot.project.service.ProjectCollabService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workspace/projects/pins")
public class WorkspaceProjectPinController {
    private final UserProjectPinService pinService;
    private final ProjectCollabService projectCollabService;

    public WorkspaceProjectPinController(UserProjectPinService pinService, ProjectCollabService projectCollabService) {
        this.pinService = pinService;
        this.projectCollabService = projectCollabService;
    }

    @GetMapping
    public R<List<Long>> list() {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(pinService.listPinnedProjectIds(userId));
    }

    @PostMapping
    public R<Void> upsert(@RequestBody UserProjectPinUpsertRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        if (req == null || req.getProjectId() == null) return R.fail(400, "projectId不能为空");
        boolean pinned = Boolean.TRUE.equals(req.getPinned());
        try {
            projectCollabService.requireMember(userId, req.getProjectId());
            pinService.setPinned(userId, req.getProjectId(), pinned);
            return R.ok();
        } catch (IllegalArgumentException e) {
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(e.getMessage())) return R.fail(403, "无权限或项目不存在");
            return R.fail(400, "操作失败");
        }
    }
}

