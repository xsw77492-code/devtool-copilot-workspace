package com.devtoolcopilot.project.pin.dto;

import lombok.Data;

@Data
public class UserProjectPinUpsertRequest {
    private Long projectId;
    private Boolean pinned;
}

