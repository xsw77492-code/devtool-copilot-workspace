package com.devtoolcopilot.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ProjectActivityItem {
    private Long id;
    private Long actorUserId;
    private String actorUsername;
    private String type;
    private String detail;
    private LocalDateTime createTime;
}

