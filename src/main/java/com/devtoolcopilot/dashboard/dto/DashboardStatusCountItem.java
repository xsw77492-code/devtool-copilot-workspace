package com.devtoolcopilot.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DashboardStatusCountItem {
    private String status;
    private Long count;
}

