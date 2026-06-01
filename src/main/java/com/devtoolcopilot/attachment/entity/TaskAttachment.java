package com.devtoolcopilot.attachment.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task_attachment")
public class TaskAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("project_id")
    private Long projectId;

    @TableField("task_id")
    private Long taskId;

    @TableField("comment_id")
    private Long commentId;

    @TableField("user_id")
    private Long userId;

    @TableField("original_name")
    private String originalName;

    @TableField("content_type")
    private String contentType;

    @TableField("size_bytes")
    private Long sizeBytes;

    @TableField("storage_key")
    private String storageKey;

    @TableField("storage_path")
    private String storagePath;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

