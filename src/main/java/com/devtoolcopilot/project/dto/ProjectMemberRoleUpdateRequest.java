package com.devtoolcopilot.project.dto;

import com.devtoolcopilot.project.entity.ProjectMemberRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProjectMemberRoleUpdateRequest {
    @NotNull
    private ProjectMemberRole role;
}

