package com.devtoolcopilot.notification.service;

import com.devtoolcopilot.notification.dto.NotificationListResponse;
import com.devtoolcopilot.notification.dto.NotificationExportResponse;
import com.devtoolcopilot.notification.dto.NotificationSettingsRequest;
import com.devtoolcopilot.notification.dto.NotificationSettingsResponse;
import com.devtoolcopilot.notification.entity.UserNotification;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface NotificationService {
    UserNotification create(Long userId, String type, String title, String content, String dataJson);

    UserNotification create(Long userId,
                            String type,
                            String title,
                            String content,
                            String dataJson,
                            Long projectId,
                            Long taskId,
                            Long commentId,
                            String groupKey);

    NotificationListResponse list(Long userId, Long cursor, Integer limit, Boolean unreadOnly);

    NotificationListResponse list(Long userId,
                                  Long cursor,
                                  Integer limit,
                                  Boolean unreadOnly,
                                  String type,
                                  Long projectId,
                                  String q);

    NotificationListResponse list(Long userId,
                                  Long cursor,
                                  Integer limit,
                                  Boolean unreadOnly,
                                  List<String> types,
                                  Long projectId,
                                  String q);

    Long unreadCount(Long userId);

    boolean markRead(Long userId, Long id);

    int markReadBatch(Long userId, List<Long> ids);

    int markAllRead(Long userId);

    int markReadByFilter(Long userId, Boolean unreadOnly, String type, Long projectId, String q);

    int markReadByFilter(Long userId, Boolean unreadOnly, List<String> types, Long projectId, String q);

    boolean deleteOne(Long userId, Long id);

    int clearRead(Long userId);

    NotificationExportResponse exportCsv(Long userId, Boolean unreadOnly, String type, Long projectId, String q);

    NotificationExportResponse exportCsv(Long userId, Boolean unreadOnly, List<String> types, Long projectId, String q);

    NotificationSettingsResponse getSettings(Long userId);

    void updateSettings(Long userId, NotificationSettingsRequest settings);

    SseEmitter connect(Long userId);
}
