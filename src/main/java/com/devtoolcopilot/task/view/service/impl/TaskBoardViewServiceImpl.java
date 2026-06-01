package com.devtoolcopilot.task.view.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.project.entity.Project;
import com.devtoolcopilot.project.mapper.ProjectMapper;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.task.view.entity.TaskBoardView;
import com.devtoolcopilot.task.view.mapper.TaskBoardViewMapper;
import com.devtoolcopilot.task.view.service.TaskBoardViewService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskBoardViewServiceImpl implements TaskBoardViewService {
    private final TaskBoardViewMapper mapper;
    private final ProjectCollabService projectCollabService;
    private final ProjectMapper projectMapper;

    public TaskBoardViewServiceImpl(TaskBoardViewMapper mapper, ProjectCollabService projectCollabService, ProjectMapper projectMapper) {
        this.mapper = mapper;
        this.projectCollabService = projectCollabService;
        this.projectMapper = projectMapper;
    }

    @Override
    public List<TaskBoardView> list(Long userId, Long projectId) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (projectId == null) throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        projectCollabService.requireMember(userId, projectId);
        return mapper.selectList(Wrappers.<TaskBoardView>lambdaQuery()
                .eq(TaskBoardView::getProjectId, projectId)
                .eq(TaskBoardView::getUserId, userId)
                .orderByDesc(TaskBoardView::getUpdateTime)
                .orderByDesc(TaskBoardView::getId));
    }

    @Override
    public TaskBoardView create(Long userId, Long projectId, String name, String color, String filtersJson) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (projectId == null) throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("NAME_REQUIRED");
        if (filtersJson == null) throw new IllegalArgumentException("FILTERS_REQUIRED");
        projectCollabService.requireMember(userId, projectId);
        ensureProjectWritable(projectId);
        TaskBoardView v = new TaskBoardView();
        v.setProjectId(projectId);
        v.setUserId(userId);
        v.setName(name.trim());
        v.setColor(color == null || color.isBlank() ? null : color.trim());
        v.setFiltersJson(filtersJson);
        try {
            mapper.insert(v);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("DUP_NAME");
        }
        return v;
    }

    @Override
    public TaskBoardView update(Long userId, Long id, String name, String color, String filtersJson) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (id == null) throw new IllegalArgumentException("ID_REQUIRED");
        TaskBoardView existing = mapper.selectById(id);
        if (existing == null) return null;
        if (!userId.equals(existing.getUserId())) {
            throw new IllegalArgumentException("FORBIDDEN");
        }
        ensureProjectWritable(existing.getProjectId());
        if (name != null && !name.isBlank()) {
            existing.setName(name.trim());
        }
        if (color != null) {
            existing.setColor(color.isBlank() ? null : color.trim());
        }
        if (filtersJson != null) {
            existing.setFiltersJson(filtersJson);
        }
        try {
            mapper.updateById(existing);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("DUP_NAME");
        }
        return existing;
    }

    @Override
    public void delete(Long userId, Long id) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (id == null) throw new IllegalArgumentException("ID_REQUIRED");
        TaskBoardView existing = mapper.selectById(id);
        if (existing == null) return;
        if (!userId.equals(existing.getUserId())) {
            throw new IllegalArgumentException("FORBIDDEN");
        }
        ensureProjectWritable(existing.getProjectId());
        mapper.deleteById(id);
    }

    private void ensureProjectWritable(Long projectId) {
        if (projectId == null) return;
        Project p = projectMapper.selectById(projectId);
        if (p != null && p.getArchived() != null && p.getArchived() == 1) {
            throw new ApiException(400, "项目已归档");
        }
    }
}
