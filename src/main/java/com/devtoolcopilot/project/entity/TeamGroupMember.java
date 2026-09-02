package com.devtoolcopilot.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team_group_member")
public class TeamGroupMember {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;

    private Long userId;

    private String role;

    @TableField("create_time")
    private LocalDateTime createTime;
}
