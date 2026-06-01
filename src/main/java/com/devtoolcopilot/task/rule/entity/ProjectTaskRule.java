package com.devtoolcopilot.task.rule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_task_rule")
public class ProjectTaskRule {
    @TableId(value = "project_id", type = IdType.INPUT)
    private Long projectId;

    private Integer requireChecklistDoneForDone;
    private LocalDateTime updateTime;
}

