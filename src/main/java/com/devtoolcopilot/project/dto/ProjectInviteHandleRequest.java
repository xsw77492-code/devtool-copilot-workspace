package com.devtoolcopilot.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectInviteHandleRequest {
    @NotBlank
    private String token;
}

