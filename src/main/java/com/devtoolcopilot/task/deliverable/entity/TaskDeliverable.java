package com.devtoolcopilot.task.deliverable.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task_deliverable")
public class TaskDeliverable {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long taskId;
    private Long userId;
    private String type;
    private String title;
    private String url;
    private String content;
    private String status;
    private Long sort;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time")
    private LocalDateTime updateTime;
}

