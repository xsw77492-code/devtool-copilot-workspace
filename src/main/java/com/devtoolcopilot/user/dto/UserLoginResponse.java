package com.devtoolcopilot.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserLoginResponse {
    private String accessToken;
    private String refreshToken;
    private UserMeResponse me;
}

