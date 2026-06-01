package com.devtoolcopilot.docgen.dto;

import lombok.Data;

@Data
public class DocGenResponse {
    private Long projectId;
    private Long assetId;
    private String filename;
    private String downloadUrl;
    private Long kbDocId;
    private Long deliverableId;
}

