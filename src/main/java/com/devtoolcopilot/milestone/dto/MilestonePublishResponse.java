package com.devtoolcopilot.milestone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MilestonePublishResponse {
    private Long milestoneId;
    private Long assetId;
}

