package com.devtoolcopilot.project.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.project.dto.ProjectCreateRequest;
import com.devtoolcopilot.project.entity.Project;
import com.devtoolcopilot.project.service.ProjectService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/project")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public R<Long> create(@RequestBody ProjectCreateRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        try {
            Long projectId = projectService.createProject(userId, req.getName(), req.getDescription());
            return R.ok(projectId);
        } catch (IllegalArgumentException e) {
            if ("PROJECT_NAME_REQUIRED".equals(e.getMessage())) {
                return R.fail(400, "项目名称不能为空");
            }
            return R.fail(400, "创建项目失败");
        }
    }

    @GetMapping("/list")
    public R<List<Project>> list(@RequestParam(required = false) Boolean includeArchived) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        return R.ok(projectService.listByUserId(userId, includeArchived));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        boolean ok = projectService.deleteProject(userId, id);
        if (!ok) {
            return R.fail(404, "项目不存在或无权限");
        }
        return R.ok();
    }

    @PostMapping("/{id}/archive")
    public R<Void> archive(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        boolean ok = projectService.archiveProject(userId, id);
        if (!ok) return R.fail(404, "项目不存在或无权限");
        return R.ok();
    }

    @PostMapping("/{id}/unarchive")
    public R<Void> unarchive(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        boolean ok = projectService.unarchiveProject(userId, id);
        if (!ok) return R.fail(404, "项目不存在或无权限");
        return R.ok();
    }
}
