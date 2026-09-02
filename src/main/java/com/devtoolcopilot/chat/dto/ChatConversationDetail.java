package com.devtoolcopilot.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatConversationDetail {
    private Long id;
    private String type;
    private String name;
    /** 群公告（单聊为 null） */
    private String announcement;
    private Long creatorId;
    private Long memberCount;
    private String myRole;
    /** 当前用户是否已开启免打扰 */
    private Boolean muted;
}
