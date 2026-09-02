package com.devtoolcopilot.release.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.release.dto.ReleaseGenerateNotesResponse;
import com.devtoolcopilot.release.dto.ReleaseCreateRequest;
import com.devtoolcopilot.release.dto.ReleaseUpdateRequest;
import com.devtoolcopilot.release.entity.ProjectRelease;
import com.devtoolcopilot.release.service.ReleaseService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/release")
public class ReleaseController {
    private final ReleaseService releaseService;

    public ReleaseController(ReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    @GetMapping("/list")
    public R<List<ProjectRelease>> list(@RequestParam Long projectId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(releaseService.listByProject(userId, projectId));
    }

    @GetMapping("/{id}")
    public R<ProjectRelease> get(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        ProjectRelease r = releaseService.get(userId, id);
        if (r == null) return R.fail(404, "Release 不存在或无权限");
        return R.ok(r);
    }

    @PostMapping
    public R<Long> create(@RequestBody ReleaseCreateRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        Long id = releaseService.create(userId, req);
        return R.ok(id);
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody ReleaseUpdateRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        boolean ok = releaseService.updateDraft(userId, id, req);
        if (!ok) return R.fail(404, "Release 不存在或无权限");
        return R.ok();
    }

    @PostMapping("/{id}/generate-notes")
    public R<ReleaseGenerateNotesResponse> generateNotes(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        ReleaseGenerateNotesResponse res = releaseService.generateNotes(userId, id);
        if (res == null) return R.fail(404, "Release 不存在或无权限");
        return R.ok(res);
    }

    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        boolean ok = releaseService.publish(userId, id);
        if (!ok) return R.fail(404, "Release 不存在或无权限");
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        boolean ok = releaseService.deleteDraft(userId, id);
        if (!ok) return R.fail(404, "Release 不存在或无权限");
        return R.ok();
    }
}
