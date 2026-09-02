package com.devtoolcopilot.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群组任务（协作模块团队任务载体）。
 * 与个人任务体系(task)完全隔离，仅挂在群组维度，
 * 支持指派/认领/状态流转/截止时间。
 */
@Data
@TableName("group_task")
public class GroupTask {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;

    private String title;

    private String description;

    /** HIGH / MEDIUM / LOW */
    private String priority;

    /** TODO / DOING / DONE */
    private String status;

    /** 指派人（可空，空表示可被成员认领） */
    private Long assigneeId;

    private Long createdBy;

    private LocalDateTime dueTime;

    private LocalDateTime doneTime;

    private Long sort;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
