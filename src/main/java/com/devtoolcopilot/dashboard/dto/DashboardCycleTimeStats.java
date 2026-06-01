package com.devtoolcopilot.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DashboardCycleTimeStats {
    private Long sampleCount;
    private Double p50Days;
    private Double p90Days;
    private Double avgDays;
}
