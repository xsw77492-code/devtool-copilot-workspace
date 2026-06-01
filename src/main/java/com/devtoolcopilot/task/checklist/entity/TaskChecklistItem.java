package com.devtoolcopilot.task.checklist.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task_checklist_item")
public class TaskChecklistItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long taskId;
    private Long userId;
    private String content;
    private Integer isDone;
    private LocalDateTime doneTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time")
    private LocalDateTime updateTime;
}

