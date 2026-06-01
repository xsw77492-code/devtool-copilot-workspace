package com.devtoolcopilot.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DashboardDayCountItem {
    private String day;
    private Long count;
}

