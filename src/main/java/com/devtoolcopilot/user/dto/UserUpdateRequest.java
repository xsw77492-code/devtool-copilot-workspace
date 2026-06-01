package com.devtoolcopilot.user.dto;

import lombok.Data;

@Data
public class UserUpdateRequest {
    private String username;
    private String email;
}
