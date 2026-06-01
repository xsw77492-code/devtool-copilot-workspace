package com.devtoolcopilot.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserEmailVerifyRequest {
    @NotBlank
    @Email
    private String email;
}
