package com.devtoolcopilot.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ChatMessageItem {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private ChatSenderItem sender;
    private String msgType;
    private String content;
    private ChatAttachmentItem attachment;
    /** 引用的目标消息（摘要信息），引用/回复时非空 */
    private ChatMessageItem replyTo;
    /** 是否已撤回：true=前端显示"消息已撤回"占位 */
    private boolean recalled;
    private LocalDateTime createTime;
}
