package com.devtoolcopilot.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team_group_invite")
public class TeamGroupInvite {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;

    private Long inviterUserId;

    private String email;

    private String role;

    private String tokenHash;

    private String status;

    private LocalDateTime expireTime;

    private Long acceptedUserId;

    private LocalDateTime handledTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
