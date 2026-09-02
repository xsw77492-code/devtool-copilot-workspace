package com.devtoolcopilot.release.dto;

import lombok.Data;

@Data
public class ReleaseCreateRequest {
    private Long projectId;
    private Long milestoneId;
    private String version;
    private String summary;
    private Boolean generateNotes;
}

