package com.devtoolcopilot.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_conversation_member")
public class ChatConversationMember {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private Long userId;

    /** OWNER / MEMBER */
    private String role;

    private Long lastReadMessageId;

    private Integer muted;

    /** 是否置顶（1=置顶） */
    private Integer pinned;

    @TableField("create_time")
    private LocalDateTime createTime;
}
