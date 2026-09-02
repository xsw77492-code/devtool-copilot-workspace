package com.devtoolcopilot.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatMemberItem {
    private Long userId;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String email;
    private String status;
}
