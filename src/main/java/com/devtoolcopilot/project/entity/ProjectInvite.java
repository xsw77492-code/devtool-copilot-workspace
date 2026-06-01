package com.devtoolcopilot.project.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_invite")
public class ProjectInvite {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long inviterUserId;
    private String email;
    private ProjectMemberRole role;
    private String tokenHash;
    private ProjectInviteStatus status;
    private LocalDateTime expireTime;
    private Integer maxUses;
    private Integer usedCount;
    private Long acceptedUserId;
    private LocalDateTime handledTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
