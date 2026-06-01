package com.devtoolcopilot.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class UserSessionsResponse {
    private List<UserSessionItem> sessions;
}
