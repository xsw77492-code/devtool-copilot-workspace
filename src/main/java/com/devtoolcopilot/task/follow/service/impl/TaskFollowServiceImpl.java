package com.devtoolcopilot.task.follow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.project.entity.Project;
import com.devtoolcopilot.project.mapper.ProjectMapper;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.task.follow.entity.TaskFollow;
import com.devtoolcopilot.task.follow.mapper.TaskFollowMapper;
import com.devtoolcopilot.task.follow.service.TaskFollowService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskFollowServiceImpl implements TaskFollowService {
    private final TaskFollowMapper followMapper;
    private final ProjectCollabService projectCollabService;
    private final ProjectMapper projectMapper;

    public TaskFollowServiceImpl(TaskFollowMapper followMapper, ProjectCollabService projectCollabService, ProjectMapper projectMapper) {
        this.followMapper = followMapper;
        this.projectCollabService = projectCollabService;
        this.projectMapper = projectMapper;
    }

    @Override
    public boolean isFollowing(Long userId, Long taskId) {
        if (userId == null || taskId == null) return false;
        Long cnt = followMapper.selectCount(
                Wrappers.<TaskFollow>lambdaQuery()
                        .eq(TaskFollow::getUserId, userId)
                        .eq(TaskFollow::getTaskId, taskId)
        );
        return cnt != null && cnt > 0;
    }

    @Override
    public void follow(Long userId, Long projectId, Long taskId) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (projectId == null) throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        if (taskId == null) throw new IllegalArgumentException("TASK_ID_REQUIRED");
        projectCollabService.requireMember(userId, projectId);
        ensureProjectWritable(projectId);
        TaskFollow exists = followMapper.selectOne(
                Wrappers.<TaskFollow>lambdaQuery()
                        .eq(TaskFollow::getUserId, userId)
                        .eq(TaskFollow::getTaskId, taskId)
                        .last("LIMIT 1")
        );
        if (exists != null) return;
        TaskFollow row = new TaskFollow();
        row.setUserId(userId);
        row.setTaskId(taskId);
        followMapper.insert(row);
    }

    @Override
    public void unfollow(Long userId, Long projectId, Long taskId) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (projectId == null) throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        if (taskId == null) throw new IllegalArgumentException("TASK_ID_REQUIRED");
        projectCollabService.requireMember(userId, projectId);
        ensureProjectWritable(projectId);
        followMapper.delete(
                Wrappers.<TaskFollow>lambdaQuery()
                        .eq(TaskFollow::getUserId, userId)
                        .eq(TaskFollow::getTaskId, taskId)
        );
    }

    @Override
    public List<Long> followerIds(Long taskId) {
        if (taskId == null) return List.of();
        List<TaskFollow> rows = followMapper.selectList(
                Wrappers.<TaskFollow>lambdaQuery()
                        .eq(TaskFollow::getTaskId, taskId)
                        .orderByDesc(TaskFollow::getId)
                        .last("LIMIT 500")
        );
        return rows.stream().map(TaskFollow::getUserId).filter(x -> x != null).distinct().toList();
    }

    private void ensureProjectWritable(Long projectId) {
        if (projectId == null) return;
        Project p = projectMapper.selectById(projectId);
        if (p != null && p.getArchived() != null && p.getArchived() == 1) {
            throw new ApiException(400, "项目已归档");
        }
    }
}
