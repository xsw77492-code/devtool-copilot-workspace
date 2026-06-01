package com.devtoolcopilot.realtime.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_presence_session")
public class ProjectPresenceSession {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("project_id")
    private Long projectId;

    @TableField("user_id")
    private Long userId;

    @TableField("ws_session_id")
    private String wsSessionId;

    @TableField("view_type")
    private String viewType;

    @TableField("view_id")
    private Long viewId;

    @TableField("last_seen_time")
    private LocalDateTime lastSeenTime;

    @TableField("is_editing")
    private Integer isEditing;

    @TableField(value = "connect_time", fill = FieldFill.INSERT)
    private LocalDateTime connectTime;

    @TableField("disconnect_time")
    private LocalDateTime disconnectTime;
}
