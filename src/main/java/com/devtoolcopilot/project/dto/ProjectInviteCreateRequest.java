package com.devtoolcopilot.project.dto;

import com.devtoolcopilot.project.entity.ProjectMemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectInviteCreateRequest {
    @NotBlank
    @Email
    private String email;

    private ProjectMemberRole role;
}

