package com.devtoolcopilot.integration.gitee.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.integration.gitee.dto.*;
import com.devtoolcopilot.integration.gitee.service.GiteeIntegrationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gitee")
public class GiteeIntegrationController {
    private final GiteeIntegrationService integrationService;

    public GiteeIntegrationController(GiteeIntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @GetMapping("/config")
    public R<GiteeRepoConfigDTO> getConfig(@RequestParam("projectId") Long projectId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            return R.ok(integrationService.getConfig(userId, projectId));
        } catch (IllegalArgumentException e) {
            return R.fail(400, "请求参数错误");
        }
    }

    @PostMapping("/config")
    public R<GiteeRepoConfigDTO> saveConfig(@RequestBody SaveGiteeRepoConfigRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            return R.ok(integrationService.saveConfig(userId, req.getProjectId(), req.getOwner(), req.getRepo(), req.getAccessToken()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("PROJECT_ID_REQUIRED".equals(msg)) return R.fail(400, "projectId不能为空");
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(msg)) return R.fail(403, "无权限或项目不存在");
            if ("OWNER_REQUIRED".equals(msg)) return R.fail(400, "owner不能为空");
            if ("REPO_REQUIRED".equals(msg)) return R.fail(400, "repo不能为空");
            if ("TOKEN_REQUIRED".equals(msg)) return R.fail(400, "accessToken不能为空");
            return R.fail(400, "请求参数错误");
        }
    }

    @GetMapping("/panel")
    public R<GiteePanelDTO> panel(@RequestParam("projectId") Long projectId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            return R.ok(integrationService.panel(userId, projectId));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("PROJECT_ID_REQUIRED".equals(msg)) return R.fail(400, "projectId不能为空");
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(msg)) return R.fail(403, "无权限或项目不存在");
            if ("GITEE_CONFIG_REQUIRED".equals(msg)) return R.fail(400, "请先绑定 Gitee 仓库与 Token");
            return R.fail(400, "请求参数错误");
        } catch (IllegalStateException e) {
            String msg = e.getMessage();
            if ("GITEE_UNAUTHORIZED".equals(msg)) return R.fail(502, "Gitee Token 无效或无权限");
            if ("GITEE_RATE_LIMIT".equals(msg)) return R.fail(502, "Gitee 触发限流");
            if ("GITEE_REQUEST_FAILED".equals(msg)) return R.fail(502, "Gitee 网络错误或超时");
            return R.fail(502, "Gitee 服务调用失败");
        }
    }

    @PostMapping("/link")
    public R<Long> link(@RequestBody TaskPrLinkRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            Long id = integrationService.linkTaskToPr(userId, req.getProjectId(), req.getTaskId(), req.getPr());
            return R.ok(id);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("PROJECT_ID_REQUIRED".equals(msg)) return R.fail(400, "projectId不能为空");
            if ("PROJECT_NOT_FOUND_OR_FORBIDDEN".equals(msg)) return R.fail(403, "无权限或项目不存在");
            if ("TASK_ID_REQUIRED".equals(msg)) return R.fail(400, "taskId不能为空");
            if ("PR_REQUIRED".equals(msg)) return R.fail(400, "PR不能为空");
            return R.fail(400, "请求参数错误");
        }
    }

    @PostMapping("/unlink")
    public R<Void> unlink(@RequestBody UnlinkRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            integrationService.unlink(userId, req.getId());
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(400, "请求参数错误");
        }
    }
}

