package com.devtoolcopilot.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_attachment")
public class ChatAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long uploaderId;

    private String originalName;

    private String contentType;

    /** IMAGE / FILE */
    private String fileType;

    private Long sizeBytes;

    private String storageKey;

    private String storagePath;

    @TableField("create_time")
    private LocalDateTime createTime;
}
