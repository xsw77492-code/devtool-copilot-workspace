package com.devtoolcopilot.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private Long senderId;

    /** TEXT / IMAGE / FILE / SYSTEM */
    private String msgType;

    private String content;

    private Long attachmentId;

    /** 引用回复的目标消息 id */
    private Long replyToId;

    /** 是否已撤回：1=已撤回（前端显示占位） */
    private Integer recalled;

    @TableField("create_time")
    private LocalDateTime createTime;
}
