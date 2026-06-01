package com.devtoolcopilot.project.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_member")
public class ProjectMember {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long userId;
    private ProjectMemberRole role;

    private Integer disabled;

    private LocalDateTime disabledTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
