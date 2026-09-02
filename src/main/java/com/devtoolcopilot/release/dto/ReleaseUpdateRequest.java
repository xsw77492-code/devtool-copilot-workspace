package com.devtoolcopilot.release.dto;

import lombok.Data;

@Data
public class ReleaseUpdateRequest {
    private Long milestoneId;
    private String version;
    private String summary;
}

