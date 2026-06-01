package com.devtoolcopilot.inbox.service;

import com.devtoolcopilot.inbox.dto.InboxListResponse;
import com.devtoolcopilot.notification.entity.UserNotification;

import java.util.List;

public interface InboxService {
    InboxListResponse list(Long userId,
                           Long cursor,
                           Integer limit,
                           Boolean unreadOnly,
                           Boolean handled,
                           String category,
                           Long projectId,
                           String q);

    int markReadBatch(Long userId, List<Long> ids);

    int markHandledBatch(Long userId, List<Long> ids);

    void upsertFromNotification(Long userId, UserNotification n);

    void upsertAssignedTask(Long userId, Long projectId, Long taskId, String taskTitle);
}

