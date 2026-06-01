package com.devtoolcopilot.user.preference.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.user.preference.dto.UserPreferenceDTO;
import com.devtoolcopilot.user.preference.dto.UserPreferenceUpdateRequest;
import com.devtoolcopilot.user.preference.service.UserPreferenceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/preferences")
public class UserPreferenceController {
    private final UserPreferenceService service;

    public UserPreferenceController(UserPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public R<UserPreferenceDTO> get() {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(service.get(userId));
    }

    @PutMapping
    public R<Void> update(@RequestBody UserPreferenceUpdateRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        service.update(userId, req);
        return R.ok();
    }
}

