package com.devtoolcopilot.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("`user`")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String email;
    private String password;

    private Integer emailVerified;

    private UserRole role;

    private Integer disabled;
    private Integer failedLoginAttempts;
    private LocalDateTime lockUntil;

    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private String lastLoginUserAgent;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time")
    private LocalDateTime updateTime;
}
