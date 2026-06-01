package com.devtoolcopilot.task.follow.service;

import java.util.List;

public interface TaskFollowService {
    boolean isFollowing(Long userId, Long taskId);

    void follow(Long userId, Long projectId, Long taskId);

    void unfollow(Long userId, Long projectId, Long taskId);

    List<Long> followerIds(Long taskId);
}

