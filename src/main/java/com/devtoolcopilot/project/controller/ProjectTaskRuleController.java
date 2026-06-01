package com.devtoolcopilot.project.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.task.rule.dto.ProjectTaskRuleDTO;
import com.devtoolcopilot.task.rule.service.ProjectTaskRuleService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/project")
public class ProjectTaskRuleController {
    private final ProjectTaskRuleService projectTaskRuleService;

    public ProjectTaskRuleController(ProjectTaskRuleService projectTaskRuleService) {
        this.projectTaskRuleService = projectTaskRuleService;
    }

    @GetMapping("/{projectId}/task-rules")
    public R<ProjectTaskRuleDTO> get(@PathVariable Long projectId) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        return R.ok(projectTaskRuleService.get(me, projectId));
    }

    @PutMapping("/{projectId}/task-rules")
    public R<ProjectTaskRuleDTO> save(@PathVariable Long projectId, @RequestBody ProjectTaskRuleDTO req) {
        Long me = UserContext.getUserId();
        if (me == null) return R.fail(401, "未登录");
        if (req == null) return R.fail(400, "请求参数错误");
        return R.ok(projectTaskRuleService.save(me, projectId, req.getRequireChecklistDoneForDone()));
    }
}

