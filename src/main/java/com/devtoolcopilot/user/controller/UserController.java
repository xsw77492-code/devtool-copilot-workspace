package com.devtoolcopilot.user.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.common.web.ClientIpUtils;
import com.devtoolcopilot.user.dto.UserEmailVerifyConfirmRequest;
import com.devtoolcopilot.user.dto.UserEmailVerifyConfirmResponse;
import com.devtoolcopilot.user.dto.UserEmailVerifyRequest;
import com.devtoolcopilot.user.dto.UserLoginRequest;
import com.devtoolcopilot.user.dto.UserLoginResponse;
import com.devtoolcopilot.user.dto.UserLogoutRequest;
import com.devtoolcopilot.user.dto.UserMeResponse;
import com.devtoolcopilot.user.dto.UserPasswordChangeRequest;
import com.devtoolcopilot.user.dto.UserPasswordResetConfirmRequest;
import com.devtoolcopilot.user.dto.UserPasswordResetRequest;
import com.devtoolcopilot.user.dto.UserRefreshRequest;
import com.devtoolcopilot.user.dto.UserRegisterRequest;
import com.devtoolcopilot.user.dto.UserSessionsResponse;
import com.devtoolcopilot.user.entity.User;
import com.devtoolcopilot.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public R<Long> register(@Valid @RequestBody UserRegisterRequest req) {
        Long userId = userService.register(req.getUsername(), req.getEmail(), req.getPassword(), req.getEmailVerifyToken());
        return R.ok(userId);
    }

    @PostMapping("/login")
    public R<UserLoginResponse> login(@Valid @RequestBody UserLoginRequest req, HttpServletRequest request) {
        String ip = ClientIpUtils.getClientIp(request);
        String ua = request.getHeader("User-Agent");
        String deviceName = request.getHeader("X-Device-Name");
        UserLoginResponse resp = userService.login(req.getUsername(), req.getPassword(), ip, ua, deviceName);
        return R.ok(resp);
    }

    @PostMapping("/refresh")
    public R<UserLoginResponse> refresh(@Valid @RequestBody UserRefreshRequest req, HttpServletRequest request) {
        String ip = ClientIpUtils.getClientIp(request);
        String ua = request.getHeader("User-Agent");
        String deviceName = request.getHeader("X-Device-Name");
        UserLoginResponse resp = userService.refresh(req.getRefreshToken(), ip, ua, deviceName);
        return R.ok(resp);
    }

    @PostMapping("/logout")
    public R<Void> logout(@Valid @RequestBody UserLogoutRequest req) {
        userService.logout(req.getRefreshToken());
        return R.ok();
    }

    @PostMapping("/password-reset/request")
    public R<Void> requestPasswordReset(@Valid @RequestBody UserPasswordResetRequest req, HttpServletRequest request) {
        String ip = ClientIpUtils.getClientIp(request);
        userService.requestPasswordReset(req.getEmail(), ip);
        return R.ok();
    }

    @PostMapping("/password-reset/confirm")
    public R<Void> confirmPasswordReset(@Valid @RequestBody UserPasswordResetConfirmRequest req) {
        userService.confirmPasswordReset(req.getToken(), req.getNewPassword());
        return R.ok();
    }

    @GetMapping("/me")
    public R<UserMeResponse> me() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        User user = userService.getById(userId);
        if (user == null) {
            return R.fail(401, "未登录");
        }
        return R.ok(new UserMeResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getDisabled(),
                user.getLastLoginTime(),
                user.getCreateTime()
        ));
    }

    @PostMapping("/logout-all")
    public R<Void> logoutAll() {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        userService.logoutAll(userId);
        return R.ok();
    }

    @PostMapping("/password/change")
    public R<Void> changePassword(@Valid @RequestBody UserPasswordChangeRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        userService.changePassword(userId, req.getOldPassword(), req.getNewPassword());
        return R.ok();
    }

    @GetMapping("/sessions")
    public R<UserSessionsResponse> sessions() {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(userService.sessions(userId));
    }

    @PostMapping("/sessions/{id}/revoke")
    public R<Void> revokeSession(@PathVariable("id") Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        userService.revokeSession(userId, id);
        return R.ok();
    }

    @PostMapping("/email-verify/request")
    public R<Void> requestEmailVerify(@Valid @RequestBody UserEmailVerifyRequest req, HttpServletRequest request) {
        String ip = ClientIpUtils.getClientIp(request);
        userService.requestEmailVerifyCode(req.getEmail(), ip);
        return R.ok();
    }

    @PostMapping("/email-verify/confirm")
    public R<UserEmailVerifyConfirmResponse> confirmEmailVerify(@Valid @RequestBody UserEmailVerifyConfirmRequest req) {
        String token = userService.confirmEmailVerifyCode(req.getEmail(), req.getCode());
        return R.ok(new UserEmailVerifyConfirmResponse(token));
    }
}
