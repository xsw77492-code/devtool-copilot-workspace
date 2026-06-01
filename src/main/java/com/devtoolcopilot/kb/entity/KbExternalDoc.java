package com.devtoolcopilot.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_external_doc")
public class KbExternalDoc {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long projectId;
    private String title;
    private String url;
    private String content;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

