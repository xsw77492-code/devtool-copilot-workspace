package com.devtoolcopilot.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserPasswordResetRequest {
    @NotBlank
    @Email
    private String email;
}

