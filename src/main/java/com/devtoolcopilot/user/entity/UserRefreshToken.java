package com.devtoolcopilot.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_refresh_token")
public class UserRefreshToken {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String tokenHash;

    private String deviceName;
    private String ip;
    private String userAgent;

    private Integer revoked;
    private LocalDateTime lastUseTime;
    private LocalDateTime expireTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

