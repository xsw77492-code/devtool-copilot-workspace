package com.devtoolcopilot.asset.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.asset.dto.AssetItem;
import com.devtoolcopilot.asset.entity.ProjectAsset;
import com.devtoolcopilot.asset.mapper.ProjectAssetMapper;
import com.devtoolcopilot.asset.service.AssetService;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.project.service.ProjectCollabService;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class AssetServiceImpl implements AssetService {
    private final ProjectAssetMapper assetMapper;
    private final ProjectCollabService projectCollabService;

    public AssetServiceImpl(ProjectAssetMapper assetMapper, ProjectCollabService projectCollabService) {
        this.assetMapper = assetMapper;
        this.projectCollabService = projectCollabService;
    }

    @Override
    public List<AssetItem> listByProject(Long userId, Long projectId, int limit) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (projectId == null) throw new ApiException(400, "projectId不能为空");
        projectCollabService.requireMember(userId, projectId);
        int lim = Math.max(1, Math.min(limit, 100));
        List<ProjectAsset> rows = assetMapper.selectList(
                Wrappers.<ProjectAsset>lambdaQuery()
                        .eq(ProjectAsset::getProjectId, projectId)
                        .orderByDesc(ProjectAsset::getId)
                        .last("LIMIT " + lim)
        );
        return rows.stream().map(this::toItem).toList();
    }

    @Override
    public DownloadFile loadDownload(Long userId, Long assetId) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (assetId == null) throw new ApiException(400, "id不能为空");
        ProjectAsset a = assetMapper.selectById(assetId);
        if (a == null) throw new ApiException(404, "文件不存在");
        projectCollabService.requireMember(userId, a.getProjectId());
        Path p = safePath(a.getStoragePath());
        if (p == null || !Files.exists(p)) throw new ApiException(404, "文件不存在");
        String ct = a.getContentType();
        if (ct == null || ct.isBlank()) ct = "application/octet-stream";
        return new DownloadFile(a.getName(), ct, p);
    }

    private AssetItem toItem(ProjectAsset a) {
        return new AssetItem(
                a.getId(),
                a.getProjectId(),
                a.getUserId(),
                a.getKind(),
                a.getName(),
                a.getContentType(),
                a.getSizeBytes(),
                a.getCreateTime()
        );
    }

    private static Path safePath(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Paths.get(raw).toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
    }
}

