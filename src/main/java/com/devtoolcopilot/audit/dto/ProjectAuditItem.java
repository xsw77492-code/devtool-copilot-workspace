package com.devtoolcopilot.audit.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectAuditItem {
    private Long id;
    private Long projectId;
    private Long actorUserId;
    private String actorUsername;
    private String actorEmail;
    private String action;
    private String targetType;
    private Long targetId;
    private String summary;
    private String detail;
    private String ip;
    private String userAgent;
    private LocalDateTime createTime;
}

