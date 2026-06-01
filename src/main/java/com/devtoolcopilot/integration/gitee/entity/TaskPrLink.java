package com.devtoolcopilot.integration.gitee.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task_pr_link")
public class TaskPrLink {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("project_id")
    private Long projectId;

    @TableField("task_id")
    private Long taskId;

    @TableField("pr_number")
    private Integer prNumber;

    @TableField("pr_url")
    private String prUrl;

    private String source;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

