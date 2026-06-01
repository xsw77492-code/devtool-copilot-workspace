package com.devtoolcopilot.kb.dto;

import lombok.Data;

@Data
public class KbExternalDocCreateRequest {
    private Long projectId;
    private String title;
    private String url;
    private String content;
}

