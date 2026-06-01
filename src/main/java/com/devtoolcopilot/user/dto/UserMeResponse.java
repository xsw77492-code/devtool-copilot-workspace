package com.devtoolcopilot.user.dto;

import com.devtoolcopilot.user.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class UserMeResponse {
    private Long id;
    private String username;
    private String email;
    private UserRole role;
    private Integer disabled;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
}
