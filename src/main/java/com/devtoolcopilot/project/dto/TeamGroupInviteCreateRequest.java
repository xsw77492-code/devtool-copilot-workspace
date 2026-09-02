package com.devtoolcopilot.project.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TeamGroupInviteCreateRequest {
    @NotBlank
    @Email
    private String email;
}
