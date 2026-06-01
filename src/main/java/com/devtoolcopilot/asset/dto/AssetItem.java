package com.devtoolcopilot.asset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class AssetItem {
    private Long id;
    private Long projectId;
    private Long userId;
    private String kind;
    private String name;
    private String contentType;
    private Long sizeBytes;
    private LocalDateTime createTime;
}

