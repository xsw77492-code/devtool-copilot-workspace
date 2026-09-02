package com.devtoolcopilot.chat.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatConversationItem {
    private Long id;
    private String type;
    private String name;
    private Long creatorId;
    private Long memberCount;
    private String myRole;
    private Long unreadCount;
    private ChatMessageItem lastMessage;
    /** 单聊对方的资料（群聊为 null） */
    private ChatSenderItem otherUser;
    /** 是否置顶 */
    private Boolean pinned;
    /** 是否消息免打扰 */
    private Boolean muted;
    /** 单聊：对方已读到的最大消息 id（用于已读回执），群聊为 null */
    private Long peerReadMessageId;
    private LocalDateTime createTime;

    public ChatConversationItem(Long id, String type, String name, Long creatorId, Long memberCount,
                                String myRole, Long unreadCount, ChatMessageItem lastMessage,
                                ChatSenderItem otherUser, LocalDateTime createTime) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.creatorId = creatorId;
        this.memberCount = memberCount;
        this.myRole = myRole;
        this.unreadCount = unreadCount;
        this.lastMessage = lastMessage;
        this.otherUser = otherUser;
        this.createTime = createTime;
    }
}
