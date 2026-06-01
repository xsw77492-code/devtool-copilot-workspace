package com.devtoolcopilot.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class UserSessionItem {
    private Long id;
    private String deviceName;
    private String ip;
    private String userAgent;
    private LocalDateTime lastUseTime;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
}
