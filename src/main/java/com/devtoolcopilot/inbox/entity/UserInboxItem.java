package com.devtoolcopilot.inbox.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_inbox_item")
public class UserInboxItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String dedupKey;

    private String category;

    private String title;

    private String content;

    private Long projectId;

    private Long taskId;

    private Long commentId;

    private Long notificationId;

    private Integer isRead;

    private LocalDateTime readTime;

    private Integer isHandled;

    private LocalDateTime handledTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

