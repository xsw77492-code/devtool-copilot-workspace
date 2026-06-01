package com.devtoolcopilot.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserLogoutRequest {
    @NotBlank
    private String refreshToken;
}

