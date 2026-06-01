package com.devtoolcopilot.project.pin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.devtoolcopilot.project.pin.entity.UserProjectPin;
import com.devtoolcopilot.project.pin.mapper.UserProjectPinMapper;
import com.devtoolcopilot.project.pin.service.UserProjectPinService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserProjectPinServiceImpl extends ServiceImpl<UserProjectPinMapper, UserProjectPin> implements UserProjectPinService {
    @Override
    public List<Long> listPinnedProjectIds(Long userId) {
        if (userId == null) return List.of();
        return this.list(Wrappers.<UserProjectPin>lambdaQuery()
                        .eq(UserProjectPin::getUserId, userId)
                        .orderByDesc(UserProjectPin::getSort)
                        .orderByDesc(UserProjectPin::getId))
                .stream()
                .map(UserProjectPin::getProjectId)
                .filter(x -> x != null && x > 0)
                .toList();
    }

    @Override
    public void setPinned(Long userId, Long projectId, boolean pinned) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (projectId == null) throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        if (!pinned) {
            this.remove(Wrappers.<UserProjectPin>lambdaQuery()
                    .eq(UserProjectPin::getUserId, userId)
                    .eq(UserProjectPin::getProjectId, projectId));
            return;
        }
        UserProjectPin existing = this.getOne(Wrappers.<UserProjectPin>lambdaQuery()
                .eq(UserProjectPin::getUserId, userId)
                .eq(UserProjectPin::getProjectId, projectId));
        long sort = System.currentTimeMillis();
        if (existing != null) {
            existing.setSort(sort);
            this.updateById(existing);
            return;
        }
        UserProjectPin row = new UserProjectPin();
        row.setUserId(userId);
        row.setProjectId(projectId);
        row.setSort(sort);
        this.save(row);
    }
}

