package com.devtoolcopilot.user.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class AdminLoginAuditItem {
    private Long id;
    private Integer success;
    private String failReason;
    private String ip;
    private String userAgent;
    private LocalDateTime createTime;
}

