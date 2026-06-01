package com.devtoolcopilot.task.template.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.project.entity.Project;
import com.devtoolcopilot.project.mapper.ProjectMapper;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.task.template.entity.TaskTemplate;
import com.devtoolcopilot.task.template.mapper.TaskTemplateMapper;
import com.devtoolcopilot.task.template.service.TaskTemplateService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskTemplateServiceImpl implements TaskTemplateService {
    private final TaskTemplateMapper mapper;
    private final ProjectCollabService projectCollabService;
    private final ProjectMapper projectMapper;

    public TaskTemplateServiceImpl(TaskTemplateMapper mapper, ProjectCollabService projectCollabService, ProjectMapper projectMapper) {
        this.mapper = mapper;
        this.projectCollabService = projectCollabService;
        this.projectMapper = projectMapper;
    }

    @Override
    public List<TaskTemplate> list(Long userId, Long projectId) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (projectId != null) {
            projectCollabService.requireMember(userId, projectId);
        }
        return mapper.selectList(Wrappers.<TaskTemplate>lambdaQuery()
                .eq(TaskTemplate::getUserId, userId)
                .and(q -> q.isNull(TaskTemplate::getProjectId).or().eq(TaskTemplate::getProjectId, projectId))
                .orderByDesc(TaskTemplate::getUpdateTime)
                .orderByDesc(TaskTemplate::getId));
    }

    @Override
    public TaskTemplate create(Long userId, Long projectId, String name, String payloadJson) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("NAME_REQUIRED");
        if (payloadJson == null) throw new IllegalArgumentException("PAYLOAD_REQUIRED");
        if (projectId != null) {
            projectCollabService.requireMember(userId, projectId);
            ensureProjectWritable(projectId);
        }
        TaskTemplate t = new TaskTemplate();
        t.setUserId(userId);
        t.setProjectId(projectId);
        t.setName(name.trim());
        t.setPayloadJson(payloadJson);
        try {
            mapper.insert(t);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("DUP_NAME");
        }
        return t;
    }

    @Override
    public TaskTemplate update(Long userId, Long id, String name, String payloadJson) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (id == null) throw new IllegalArgumentException("ID_REQUIRED");
        TaskTemplate existing = mapper.selectById(id);
        if (existing == null) return null;
        if (!userId.equals(existing.getUserId())) {
            throw new IllegalArgumentException("FORBIDDEN");
        }
        if (existing.getProjectId() != null) {
            ensureProjectWritable(existing.getProjectId());
        }
        if (name != null && !name.isBlank()) existing.setName(name.trim());
        if (payloadJson != null) existing.setPayloadJson(payloadJson);
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
        TaskTemplate existing = mapper.selectById(id);
        if (existing == null) return;
        if (!userId.equals(existing.getUserId())) {
            throw new IllegalArgumentException("FORBIDDEN");
        }
        if (existing.getProjectId() != null) {
            ensureProjectWritable(existing.getProjectId());
        }
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
