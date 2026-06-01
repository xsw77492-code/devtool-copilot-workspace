package com.devtoolcopilot.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserEmailVerifyConfirmResponse {
    private String verifyToken;
}
