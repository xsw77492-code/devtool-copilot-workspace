package com.devtoolcopilot.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.devtoolcopilot.user.dto.UserLoginResponse;
import com.devtoolcopilot.user.entity.User;

public interface UserService extends IService<User> {
    Long register(String username, String email, String rawPassword, String emailVerifyToken);

    UserLoginResponse login(String username, String rawPassword, String ip, String userAgent, String deviceName);

    UserLoginResponse refresh(String refreshToken, String ip, String userAgent, String deviceName);

    void logout(String refreshToken);

    void requestPasswordReset(String email, String ip);

    void confirmPasswordReset(String token, String newPassword);

    void logoutAll(Long userId);

    void changePassword(Long userId, String oldPassword, String newPassword);

    com.devtoolcopilot.user.dto.UserSessionsResponse sessions(Long userId);

    void revokeSession(Long userId, Long sessionId);

    void requestEmailVerifyCode(String email, String ip);

    String confirmEmailVerifyCode(String email, String code);
}
