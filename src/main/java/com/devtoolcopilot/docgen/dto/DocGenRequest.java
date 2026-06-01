package com.devtoolcopilot.docgen.dto;

import lombok.Data;

@Data
public class DocGenRequest {
    private Long projectId;
    private String requirement;
    private String title;
    private String style;
    private DocGenSaveTo saveTo;
    private Long taskId;
}

