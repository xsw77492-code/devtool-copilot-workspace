package com.devtoolcopilot.audit.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_audit_log")
public class ProjectAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long actorUserId;
    private String action;
    private String targetType;
    private Long targetId;
    private String summary;
    private String detail;
    private String ip;
    private String userAgent;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

