package com.devtoolcopilot.project.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProjectMemberDisabledRequest {
    @NotNull
    private Boolean disabled;
}

