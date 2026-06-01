package com.devtoolcopilot.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.common.jwt.JwtTokenService;
import com.devtoolcopilot.user.config.AuthSecurityProperties;
import com.devtoolcopilot.user.config.DevtoolMailProperties;
import com.devtoolcopilot.user.dto.UserLoginResponse;
import com.devtoolcopilot.user.dto.UserMeResponse;
import com.devtoolcopilot.user.dto.UserSessionItem;
import com.devtoolcopilot.user.dto.UserSessionsResponse;
import com.devtoolcopilot.user.entity.UserEmailAction;
import com.devtoolcopilot.user.entity.UserEmailVerifyCode;
import com.devtoolcopilot.user.entity.UserEmailVerifyToken;
import com.devtoolcopilot.user.entity.User;
import com.devtoolcopilot.user.entity.UserLoginAudit;
import com.devtoolcopilot.user.entity.UserPasswordResetToken;
import com.devtoolcopilot.user.entity.UserRefreshToken;
import com.devtoolcopilot.user.entity.UserRole;
import com.devtoolcopilot.user.mapper.UserEmailActionMapper;
import com.devtoolcopilot.user.mapper.UserEmailVerifyCodeMapper;
import com.devtoolcopilot.user.mapper.UserEmailVerifyTokenMapper;
import com.devtoolcopilot.user.mapper.UserLoginAuditMapper;
import com.devtoolcopilot.user.mapper.UserMapper;
import com.devtoolcopilot.user.mapper.UserPasswordResetTokenMapper;
import com.devtoolcopilot.user.mapper.UserRefreshTokenMapper;
import com.devtoolcopilot.user.service.UserService;
import com.devtoolcopilot.user.util.TokenUtils;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthSecurityProperties authSecurityProperties;
    private final JavaMailSender mailSender;
    private final DevtoolMailProperties mailProperties;
    private final UserRefreshTokenMapper refreshTokenMapper;
    private final UserPasswordResetTokenMapper passwordResetTokenMapper;
    private final UserLoginAuditMapper loginAuditMapper;
    private final UserEmailActionMapper emailActionMapper;
    private final UserEmailVerifyCodeMapper emailVerifyCodeMapper;
    private final UserEmailVerifyTokenMapper emailVerifyTokenMapper;

    public UserServiceImpl(PasswordEncoder passwordEncoder,
                           JwtTokenService jwtTokenService,
                           AuthSecurityProperties authSecurityProperties,
                           JavaMailSender mailSender,
                           DevtoolMailProperties mailProperties,
                           UserRefreshTokenMapper refreshTokenMapper,
                           UserPasswordResetTokenMapper passwordResetTokenMapper,
                           UserLoginAuditMapper loginAuditMapper,
                           UserEmailActionMapper emailActionMapper,
                           UserEmailVerifyCodeMapper emailVerifyCodeMapper,
                           UserEmailVerifyTokenMapper emailVerifyTokenMapper) {
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.authSecurityProperties = authSecurityProperties;
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordResetTokenMapper = passwordResetTokenMapper;
        this.loginAuditMapper = loginAuditMapper;
        this.emailActionMapper = emailActionMapper;
        this.emailVerifyCodeMapper = emailVerifyCodeMapper;
        this.emailVerifyTokenMapper = emailVerifyTokenMapper;
    }

    @Override
    public Long register(String username, String email, String rawPassword, String emailVerifyToken) {
        if (username == null || username.isBlank()) {
            throw new ApiException(400, "用户名不能为空");
        }
        if (email == null || email.isBlank()) {
            throw new ApiException(400, "邮箱不能为空");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new ApiException(400, "密码不能为空");
        }
        boolean exists = this.exists(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (exists) {
            throw new ApiException(400, "用户名已存在");
        }
        boolean emailExists = this.exists(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
        if (emailExists) {
            throw new ApiException(400, "邮箱已存在");
        }

        int emailVerified = 0;
        if (emailVerifyToken != null && !emailVerifyToken.isBlank()) {
            String tokenHash = TokenUtils.sha256Hex(emailVerifyToken);
            UserEmailVerifyToken t = emailVerifyTokenMapper.selectOne(
                    Wrappers.<UserEmailVerifyToken>lambdaQuery()
                            .eq(UserEmailVerifyToken::getTokenHash, tokenHash)
            );
            if (t == null
                    || (t.getUsed() != null && t.getUsed() == 1)
                    || t.getExpireTime() == null
                    || t.getExpireTime().isBefore(LocalDateTime.now())
                    || t.getEmail() == null
                    || !t.getEmail().equalsIgnoreCase(email)) {
                throw new ApiException(400, "邮箱验证码未通过");
            }
            t.setUsed(1);
            emailVerifyTokenMapper.updateById(t);
            emailVerified = 1;
        } else if (authSecurityProperties.isRequireEmailVerification()) {
            throw new ApiException(400, "请先完成邮箱验证");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setEmailVerified(emailVerified);
        user.setRole(UserRole.USER);
        user.setDisabled(0);
        user.setFailedLoginAttempts(0);
        this.save(user);
        return user.getId();
    }

    @Override
    public UserLoginResponse login(String username, String rawPassword, String ip, String userAgent, String deviceName) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new ApiException(400, "用户名或密码不能为空");
        }

        if (ip != null && !ip.isBlank()) {
            Long failedIn10Min = loginAuditMapper.selectCount(
                    Wrappers.<UserLoginAudit>lambdaQuery()
                            .eq(UserLoginAudit::getIp, ip)
                            .eq(UserLoginAudit::getSuccess, 0)
                            .ge(UserLoginAudit::getCreateTime, LocalDateTime.now().minusMinutes(10))
            );
            if (failedIn10Min != null && failedIn10Min >= authSecurityProperties.getLoginFailMaxPerIpIn10Min()) {
                writeLoginAudit(null, username, 0, "RATE_LIMIT", ip, userAgent);
                throw new ApiException(429, "请求过于频繁，请稍后再试");
            }
        }

        User user = this.getOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (user == null) {
            writeLoginAudit(null, username, 0, "INVALID_CREDENTIALS", ip, userAgent);
            throw new ApiException(401, "用户名或密码错误");
        }
        if (user.getDisabled() != null && user.getDisabled() == 1) {
            writeLoginAudit(user.getId(), username, 0, "DISABLED", ip, userAgent);
            throw new ApiException(403, "账号已被禁用");
        }
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(LocalDateTime.now())) {
            writeLoginAudit(user.getId(), username, 0, "LOCKED", ip, userAgent);
            throw new ApiException(423, "账号已锁定，请稍后再试");
        }

        boolean ok = passwordEncoder.matches(rawPassword, user.getPassword());
        if (!ok) {
            int next = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
            User update = new User();
            update.setId(user.getId());
            update.setFailedLoginAttempts(next);
            if (next >= authSecurityProperties.getMaxFailedLoginAttempts()) {
                update.setLockUntil(LocalDateTime.now().plusMinutes(authSecurityProperties.getLockMinutes()));
            }
            this.updateById(update);
            writeLoginAudit(user.getId(), username, 0, "INVALID_CREDENTIALS", ip, userAgent);
            throw new ApiException(401, "用户名或密码错误");
        }

        User update = new User();
        update.setId(user.getId());
        update.setFailedLoginAttempts(0);
        update.setLockUntil(null);
        update.setLastLoginTime(LocalDateTime.now());
        update.setLastLoginIp(ip);
        update.setLastLoginUserAgent(userAgent);
        this.updateById(update);

        writeLoginAudit(user.getId(), username, 1, null, ip, userAgent);

        String refreshToken = issueRefreshToken(user.getId(), deviceName, ip, userAgent);
        String accessToken = jwtTokenService.generateToken(user.getId());
        User fresh = this.getById(user.getId());
        return new UserLoginResponse(accessToken, refreshToken, toMeResponse(fresh));
    }

    @Override
    public UserLoginResponse refresh(String refreshToken, String ip, String userAgent, String deviceName) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(400, "refreshToken不能为空");
        }
        String tokenHash = TokenUtils.sha256Hex(refreshToken);
        UserRefreshToken db = refreshTokenMapper.selectOne(
                Wrappers.<UserRefreshToken>lambdaQuery().eq(UserRefreshToken::getTokenHash, tokenHash)
        );
        if (db == null || (db.getRevoked() != null && db.getRevoked() == 1)) {
            throw new ApiException(401, "refresh token无效");
        }
        if (db.getExpireTime() == null || db.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new ApiException(401, "refresh token已过期");
        }
        User user = this.getById(db.getUserId());
        if (user == null) {
            throw new ApiException(401, "refresh token无效");
        }
        if (user.getDisabled() != null && user.getDisabled() == 1) {
            throw new ApiException(403, "账号已被禁用");
        }

        db.setRevoked(1);
        db.setLastUseTime(LocalDateTime.now());
        refreshTokenMapper.updateById(db);

        String newRefreshToken = issueRefreshToken(user.getId(), deviceName, ip, userAgent);
        String accessToken = jwtTokenService.generateToken(user.getId());
        return new UserLoginResponse(accessToken, newRefreshToken, toMeResponse(user));
    }

    @Override
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        String tokenHash = TokenUtils.sha256Hex(refreshToken);
        UserRefreshToken db = refreshTokenMapper.selectOne(
                Wrappers.<UserRefreshToken>lambdaQuery().eq(UserRefreshToken::getTokenHash, tokenHash)
        );
        if (db == null) return;
        db.setRevoked(1);
        db.setLastUseTime(LocalDateTime.now());
        refreshTokenMapper.updateById(db);
    }

    @Override
    public void requestPasswordReset(String email, String ip) {
        if (email == null || email.isBlank()) {
            throw new ApiException(400, "邮箱不能为空");
        }

        Long emailCnt = emailActionMapper.selectCount(
                Wrappers.<UserEmailAction>lambdaQuery()
                        .eq(UserEmailAction::getAction, "PASSWORD_RESET")
                        .eq(UserEmailAction::getEmail, email)
                        .ge(UserEmailAction::getCreateTime, LocalDateTime.now().minusHours(1))
        );
        if (emailCnt != null && emailCnt >= authSecurityProperties.getPasswordResetMaxPerEmailPerHour()) return;

        if (ip != null && !ip.isBlank()) {
            Long ipCnt = emailActionMapper.selectCount(
                    Wrappers.<UserEmailAction>lambdaQuery()
                            .eq(UserEmailAction::getAction, "PASSWORD_RESET")
                            .eq(UserEmailAction::getIp, ip)
                            .ge(UserEmailAction::getCreateTime, LocalDateTime.now().minusHours(1))
            );
            if (ipCnt != null && ipCnt >= authSecurityProperties.getPasswordResetMaxPerIpPerHour()) return;
        }

        User user = this.getOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
        if (user == null) return;
        if (user.getDisabled() != null && user.getDisabled() == 1) return;

        UserEmailAction act = new UserEmailAction();
        act.setAction("PASSWORD_RESET");
        act.setEmail(email);
        act.setIp(ip);
        emailActionMapper.insert(act);

        String token = TokenUtils.newOpaqueToken();
        String tokenHash = TokenUtils.sha256Hex(token);

        UserPasswordResetToken t = new UserPasswordResetToken();
        t.setUserId(user.getId());
        t.setTokenHash(tokenHash);
        t.setUsed(0);
        t.setExpireTime(LocalDateTime.now().plusMinutes(authSecurityProperties.getPasswordResetExpireMinutes()));
        passwordResetTokenMapper.insert(t);

        String resetUrl = authSecurityProperties.getPasswordResetLinkBase() + token;
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(mailProperties.getFrom());
        msg.setTo(email);
        msg.setSubject("DevTool Copilot 密码重置");
        msg.setText("请打开以下链接重置密码（有效期 " + authSecurityProperties.getPasswordResetExpireMinutes() + " 分钟）：\n" + resetUrl);
        try {
            mailSender.send(msg);
        } catch (Exception e) {
            throw new ApiException(500, "邮件发送失败，请稍后重试");
        }
    }

    @Override
    public void confirmPasswordReset(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new ApiException(400, "token不能为空");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new ApiException(400, "新密码不能为空");
        }

        String tokenHash = TokenUtils.sha256Hex(token);
        UserPasswordResetToken t = passwordResetTokenMapper.selectOne(
                Wrappers.<UserPasswordResetToken>lambdaQuery().eq(UserPasswordResetToken::getTokenHash, tokenHash)
        );
        if (t == null || (t.getUsed() != null && t.getUsed() == 1)) {
            throw new ApiException(400, "token无效");
        }
        if (t.getExpireTime() == null || t.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new ApiException(400, "token已过期");
        }

        User user = this.getById(t.getUserId());
        if (user == null) {
            throw new ApiException(400, "token无效");
        }
        if (user.getDisabled() != null && user.getDisabled() == 1) {
            throw new ApiException(403, "账号已被禁用");
        }

        User update = new User();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(newPassword));
        this.updateById(update);

        t.setUsed(1);
        passwordResetTokenMapper.updateById(t);

        refreshTokenMapper.update(
                null,
                Wrappers.<UserRefreshToken>lambdaUpdate()
                        .eq(UserRefreshToken::getUserId, user.getId())
                        .set(UserRefreshToken::getRevoked, 1)
                        .set(UserRefreshToken::getLastUseTime, LocalDateTime.now())
        );
    }

    @Override
    public void logoutAll(Long userId) {
        if (userId == null) return;
        refreshTokenMapper.update(
                null,
                Wrappers.<UserRefreshToken>lambdaUpdate()
                        .eq(UserRefreshToken::getUserId, userId)
                        .set(UserRefreshToken::getRevoked, 1)
                        .set(UserRefreshToken::getLastUseTime, LocalDateTime.now())
        );
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null) throw new ApiException(401, "未登录");
        User user = this.getById(userId);
        if (user == null) throw new ApiException(401, "未登录");
        if (user.getDisabled() != null && user.getDisabled() == 1) throw new ApiException(403, "账号已被禁用");
        if (oldPassword == null || oldPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new ApiException(400, "参数错误");
        }
        boolean ok = passwordEncoder.matches(oldPassword, user.getPassword());
        if (!ok) throw new ApiException(401, "原密码错误");
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        this.updateById(update);
        logoutAll(userId);
    }

    @Override
    public UserSessionsResponse sessions(Long userId) {
        if (userId == null) throw new ApiException(401, "未登录");
        List<UserRefreshToken> list = refreshTokenMapper.selectList(
                Wrappers.<UserRefreshToken>lambdaQuery()
                        .eq(UserRefreshToken::getUserId, userId)
                        .eq(UserRefreshToken::getRevoked, 0)
                        .ge(UserRefreshToken::getExpireTime, LocalDateTime.now())
                        .orderByDesc(UserRefreshToken::getLastUseTime)
                        .last("limit 50")
        );
        List<UserSessionItem> items = list.stream()
                .map(x -> new UserSessionItem(
                        x.getId(),
                        x.getDeviceName(),
                        x.getIp(),
                        x.getUserAgent(),
                        x.getLastUseTime(),
                        x.getExpireTime(),
                        x.getCreateTime()
                ))
                .toList();
        return new UserSessionsResponse(items);
    }

    @Override
    public void revokeSession(Long userId, Long sessionId) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (sessionId == null) throw new ApiException(400, "参数错误");
        UserRefreshToken t = refreshTokenMapper.selectById(sessionId);
        if (t == null || t.getUserId() == null || !t.getUserId().equals(userId)) return;
        t.setRevoked(1);
        t.setLastUseTime(LocalDateTime.now());
        refreshTokenMapper.updateById(t);
    }

    @Override
    public void requestEmailVerifyCode(String email, String ip) {
        if (email == null || email.isBlank()) throw new ApiException(400, "邮箱不能为空");

        Long emailCnt = emailActionMapper.selectCount(
                Wrappers.<UserEmailAction>lambdaQuery()
                        .eq(UserEmailAction::getAction, "EMAIL_VERIFY")
                        .eq(UserEmailAction::getEmail, email)
                        .ge(UserEmailAction::getCreateTime, LocalDateTime.now().minusHours(1))
        );
        if (emailCnt != null && emailCnt >= 5) throw new ApiException(429, "请求过于频繁，请稍后再试");

        Long ipCnt = null;
        if (ip != null && !ip.isBlank()) {
            ipCnt = emailActionMapper.selectCount(
                    Wrappers.<UserEmailAction>lambdaQuery()
                            .eq(UserEmailAction::getAction, "EMAIL_VERIFY")
                            .eq(UserEmailAction::getIp, ip)
                            .ge(UserEmailAction::getCreateTime, LocalDateTime.now().minusHours(1))
            );
        }
        if (ipCnt != null && ipCnt >= 20) throw new ApiException(429, "请求过于频繁，请稍后再试");

        UserEmailAction act = new UserEmailAction();
        act.setAction("EMAIL_VERIFY");
        act.setEmail(email);
        act.setIp(ip);
        emailActionMapper.insert(act);

        String code = TokenUtils.newNumericCode(6);
        UserEmailVerifyCode c = new UserEmailVerifyCode();
        c.setEmail(email);
        c.setCodeHash(TokenUtils.sha256Hex(code));
        c.setUsed(0);
        c.setExpireTime(LocalDateTime.now().plusMinutes(authSecurityProperties.getEmailVerifyExpireMinutes()));
        emailVerifyCodeMapper.insert(c);

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(mailProperties.getFrom());
        msg.setTo(email);
        msg.setSubject("DevTool Copilot 邮箱验证码");
        msg.setText("你的验证码是：" + code + "\n有效期 " + authSecurityProperties.getEmailVerifyExpireMinutes() + " 分钟。");
        try {
            mailSender.send(msg);
        } catch (Exception e) {
            throw new ApiException(500, "邮件发送失败，请稍后重试");
        }
    }

    @Override
    public String confirmEmailVerifyCode(String email, String code) {
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            throw new ApiException(400, "参数错误");
        }
        String codeHash = TokenUtils.sha256Hex(code);
        UserEmailVerifyCode c = emailVerifyCodeMapper.selectOne(
                Wrappers.<UserEmailVerifyCode>lambdaQuery()
                        .eq(UserEmailVerifyCode::getEmail, email)
                        .eq(UserEmailVerifyCode::getCodeHash, codeHash)
                        .eq(UserEmailVerifyCode::getUsed, 0)
                        .ge(UserEmailVerifyCode::getExpireTime, LocalDateTime.now())
                        .orderByDesc(UserEmailVerifyCode::getId)
                        .last("limit 1")
        );
        if (c == null) throw new ApiException(400, "验证码无效或已过期");
        c.setUsed(1);
        emailVerifyCodeMapper.updateById(c);

        String token = TokenUtils.newOpaqueToken();
        UserEmailVerifyToken t = new UserEmailVerifyToken();
        t.setEmail(email);
        t.setTokenHash(TokenUtils.sha256Hex(token));
        t.setUsed(0);
        t.setExpireTime(LocalDateTime.now().plusMinutes(30));
        emailVerifyTokenMapper.insert(t);
        return token;
    }

    private String issueRefreshToken(Long userId, String deviceName, String ip, String userAgent) {
        String token = TokenUtils.newOpaqueToken();
        UserRefreshToken rt = new UserRefreshToken();
        rt.setUserId(userId);
        rt.setTokenHash(TokenUtils.sha256Hex(token));
        rt.setDeviceName(deviceName);
        rt.setIp(ip);
        rt.setUserAgent(userAgent);
        rt.setRevoked(0);
        rt.setLastUseTime(LocalDateTime.now());
        rt.setExpireTime(LocalDateTime.now().plusDays(authSecurityProperties.getRefreshTokenExpireDays()));
        refreshTokenMapper.insert(rt);
        return token;
    }

    private void writeLoginAudit(Long userId, String username, int success, String reason, String ip, String userAgent) {
        try {
            UserLoginAudit a = new UserLoginAudit();
            a.setUserId(userId);
            a.setUsername(username);
            a.setSuccess(success);
            a.setFailReason(reason);
            a.setIp(ip);
            a.setUserAgent(userAgent);
            loginAuditMapper.insert(a);
        } catch (Exception ignored) {
        }
    }

    private static UserMeResponse toMeResponse(User user) {
        if (user == null) return null;
        return new UserMeResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getDisabled(),
                user.getLastLoginTime(),
                user.getCreateTime()
        );
    }
}
