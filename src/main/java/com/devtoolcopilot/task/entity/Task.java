package com.devtoolcopilot.task.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("`task`")
public class Task {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("project_id")
    private Long projectId;

    private String title;
    private TaskStatus status;

    private String description;

    @TableField("acceptance_criteria")
    private String acceptanceCriteria;

    private String priority;

    private String tags;

    private String assignee;

    @TableField("assignee_id")
    private Long assigneeId;

    @TableField("due_time")
    private LocalDateTime dueTime;

    @TableField("due_reminded_time")
    private LocalDateTime dueRemindedTime;

    @TableField("board_sort")
    private Long boardSort;

    @TableField("started_time")
    private LocalDateTime startedTime;

    @TableField("done_time")
    private LocalDateTime doneTime;

    @TableField("milestone_id")
    private Long milestoneId;

    @TableField("parent_task_id")
    private Long parentTaskId;

    private String type;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
