package com.devtoolcopilot.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_conversation")
public class ChatConversation {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** SINGLE / GROUP */
    private String type;

    private String name;

    /** 群公告（仅 GROUP 使用） */
    private String announcement;

    private Long creatorId;

    private Long lastMessageId;

    private LocalDateTime lastMessageAt;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
