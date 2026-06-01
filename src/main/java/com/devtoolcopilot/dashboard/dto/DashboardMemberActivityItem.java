package com.devtoolcopilot.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DashboardMemberActivityItem {
    private Long userId;
    private String username;
    private Long actionCount;
}

