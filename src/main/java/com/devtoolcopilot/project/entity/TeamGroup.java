package com.devtoolcopilot.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team_group")
public class TeamGroup {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String systemKey;

    private String name;

    private Long ownerUserId;

    private Integer archived;

    private Long sort;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
