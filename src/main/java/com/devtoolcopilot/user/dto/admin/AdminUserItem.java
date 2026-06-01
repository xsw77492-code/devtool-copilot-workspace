package com.devtoolcopilot.user.dto.admin;

import com.devtoolcopilot.user.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class AdminUserItem {
    private Long id;
    private String username;
    private String email;
    private UserRole role;
    private Integer disabled;
    private Integer failedLoginAttempts;
    private LocalDateTime lockUntil;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private LocalDateTime createTime;
}

