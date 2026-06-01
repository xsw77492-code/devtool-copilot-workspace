package com.devtoolcopilot.milestone.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_milestone")
public class ProjectMilestone {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("project_id")
    private Long projectId;

    @TableField("user_id")
    private Long userId;

    private String name;
    private String description;
    private String status;

    @TableField("release_asset_id")
    private Long releaseAssetId;

    @TableField("due_time")
    private LocalDateTime dueTime;

    @TableField("published_time")
    private LocalDateTime publishedTime;

    @TableField("archived_time")
    private LocalDateTime archivedTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
