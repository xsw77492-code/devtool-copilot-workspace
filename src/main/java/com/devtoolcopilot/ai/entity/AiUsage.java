package com.devtoolcopilot.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_usage")
public class AiUsage {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long projectId;

    @TableField("type")
    private String type;

    @TableField("prompt_tokens")
    private Long promptTokens;

    @TableField("completion_tokens")
    private Long completionTokens;

    @TableField("total_tokens")
    private Long totalTokens;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
