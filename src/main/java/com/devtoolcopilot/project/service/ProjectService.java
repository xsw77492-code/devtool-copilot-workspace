package com.devtoolcopilot.project.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.devtoolcopilot.project.entity.Project;

import java.util.List;

public interface ProjectService extends IService<Project> {
    Long createProject(Long userId, String name, String description);

    List<Project> listByUserId(Long userId, Boolean includeArchived);

    boolean deleteProject(Long userId, Long projectId);

    boolean archiveProject(Long userId, Long projectId);

    boolean unarchiveProject(Long userId, Long projectId);
}
