package com.devtoolcopilot.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_login_audit")
public class UserLoginAudit {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String username;
    private Integer success;
    private String failReason;
    private String ip;
    private String userAgent;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

