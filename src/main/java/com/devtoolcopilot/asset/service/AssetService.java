package com.devtoolcopilot.asset.service;

import com.devtoolcopilot.asset.dto.AssetItem;

import java.nio.file.Path;
import java.util.List;

public interface AssetService {
    record DownloadFile(String filename, String contentType, Path path) {
    }

    List<AssetItem> listByProject(Long userId, Long projectId, int limit);

    DownloadFile loadDownload(Long userId, Long assetId);
}

