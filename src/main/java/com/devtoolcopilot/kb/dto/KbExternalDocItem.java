package com.devtoolcopilot.kb.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KbExternalDocItem {
    private Long id;
    private Long projectId;
    private String title;
    private String url;
    private LocalDateTime updateTime;
}

