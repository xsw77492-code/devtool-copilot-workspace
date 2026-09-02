package com.devtoolcopilot.release.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReleaseGenerateNotesResponse {
    private Long releaseId;
    private Long assetId;
}

