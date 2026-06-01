package com.devtoolcopilot.notification.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_notification")
public class UserNotification {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long projectId;

    private Long taskId;

    private Long commentId;

    @TableField("group_key")
    private String groupKey;

    private Integer aggCount;

    private String type;

    private String title;

    private String content;

    @TableField("data_json")
    private String dataJson;

    @TableField("is_read")
    private Integer isRead;

    private LocalDateTime readTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
