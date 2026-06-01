package com.devtoolcopilot.milestone.service;

import com.devtoolcopilot.milestone.dto.MilestonePublishResponse;
import com.devtoolcopilot.milestone.entity.ProjectMilestone;

import java.util.List;

public interface MilestoneService {
    List<ProjectMilestone> listByProject(Long userId, Long projectId, Boolean includeArchived);

    Long create(Long userId, Long projectId, String name, String description, Long dueTime);

    MilestonePublishResponse publish(Long userId, Long milestoneId);

    boolean archive(Long userId, Long milestoneId);

    boolean unarchive(Long userId, Long milestoneId);
}

