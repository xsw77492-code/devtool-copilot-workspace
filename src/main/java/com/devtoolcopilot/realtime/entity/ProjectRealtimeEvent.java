package com.devtoolcopilot.realtime.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_realtime_event")
public class ProjectRealtimeEvent {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long actorUserId;
    private String type;
    private String payloadJson;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

