package com.devtoolcopilot.user.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.user.dto.admin.AdminLoginAuditItem;
import com.devtoolcopilot.user.dto.admin.AdminResetPasswordRequest;
import com.devtoolcopilot.user.dto.admin.AdminUpdateUserStatusRequest;
import com.devtoolcopilot.user.dto.admin.AdminUserItem;
import com.devtoolcopilot.user.entity.User;
import com.devtoolcopilot.user.entity.UserLoginAudit;
import com.devtoolcopilot.user.entity.UserRefreshToken;
import com.devtoolcopilot.user.entity.UserRole;
import com.devtoolcopilot.user.mapper.UserLoginAuditMapper;
import com.devtoolcopilot.user.mapper.UserRefreshTokenMapper;
import com.devtoolcopilot.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final UserLoginAuditMapper loginAuditMapper;
    private final UserRefreshTokenMapper refreshTokenMapper;

    public AdminUserController(UserService userService,
                               PasswordEncoder passwordEncoder,
                               UserLoginAuditMapper loginAuditMapper,
                               UserRefreshTokenMapper refreshTokenMapper) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.loginAuditMapper = loginAuditMapper;
        this.refreshTokenMapper = refreshTokenMapper;
    }

    @GetMapping
    public R<List<AdminUserItem>> list() {
        requireAdmin();
        List<User> users = userService.list(Wrappers.<User>lambdaQuery().orderByDesc(User::getId));
        List<AdminUserItem> out = users.stream()
                .map(u -> new AdminUserItem(
                        u.getId(),
                        u.getUsername(),
                        u.getEmail(),
                        u.getRole(),
                        u.getDisabled(),
                        u.getFailedLoginAttempts(),
                        u.getLockUntil(),
                        u.getLastLoginTime(),
                        u.getLastLoginIp(),
                        u.getCreateTime()
                ))
                .toList();
        return R.ok(out);
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable("id") Long id, @Valid @RequestBody AdminUpdateUserStatusRequest req) {
        requireAdmin();
        User user = userService.getById(id);
        if (user == null) {
            throw new ApiException(404, "用户不存在");
        }
        User update = new User();
        update.setId(id);
        update.setDisabled(Boolean.TRUE.equals(req.getDisabled()) ? 1 : 0);
        userService.updateById(update);
        return R.ok();
    }

    @PostMapping("/{id}/reset-password")
    public R<Void> resetPassword(@PathVariable("id") Long id, @Valid @RequestBody AdminResetPasswordRequest req) {
        requireAdmin();
        User user = userService.getById(id);
        if (user == null) {
            throw new ApiException(404, "用户不存在");
        }
        User update = new User();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userService.updateById(update);

        refreshTokenMapper.update(
                null,
                Wrappers.<UserRefreshToken>lambdaUpdate()
                        .eq(UserRefreshToken::getUserId, id)
                        .set(UserRefreshToken::getRevoked, 1)
                        .set(UserRefreshToken::getLastUseTime, LocalDateTime.now())
        );
        return R.ok();
    }

    @GetMapping("/{id}/login-audits")
    public R<List<AdminLoginAuditItem>> audits(@PathVariable("id") Long id,
                                               @RequestParam(value = "limit", required = false) Integer limit) {
        requireAdmin();
        int l = (limit == null || limit <= 0) ? 20 : Math.min(limit, 200);
        List<UserLoginAudit> list = loginAuditMapper.selectList(
                Wrappers.<UserLoginAudit>lambdaQuery()
                        .eq(UserLoginAudit::getUserId, id)
                        .orderByDesc(UserLoginAudit::getId)
                        .last("LIMIT " + l)
        );
        List<AdminLoginAuditItem> out = list.stream()
                .map(a -> new AdminLoginAuditItem(
                        a.getId(),
                        a.getSuccess(),
                        a.getFailReason(),
                        a.getIp(),
                        a.getUserAgent(),
                        a.getCreateTime()
                ))
                .toList();
        return R.ok(out);
    }

    private void requireAdmin() {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new ApiException(401, "未登录");
        User u = userService.getById(userId);
        if (u == null) throw new ApiException(401, "未登录");
        if (u.getDisabled() != null && u.getDisabled() == 1) throw new ApiException(403, "账号已被禁用");
        if (u.getRole() != UserRole.ADMIN) throw new ApiException(403, "无权限");
    }
}

