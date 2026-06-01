package com.devtoolcopilot.inbox.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.inbox.dto.InboxItem;
import com.devtoolcopilot.inbox.dto.InboxListResponse;
import com.devtoolcopilot.inbox.entity.UserInboxItem;
import com.devtoolcopilot.inbox.mapper.UserInboxItemMapper;
import com.devtoolcopilot.inbox.service.InboxService;
import com.devtoolcopilot.notification.entity.UserNotification;
import com.devtoolcopilot.notification.mapper.UserNotificationMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class InboxServiceImpl implements InboxService {
    private final UserInboxItemMapper inboxItemMapper;
    private final UserNotificationMapper notificationMapper;

    public InboxServiceImpl(UserInboxItemMapper inboxItemMapper, UserNotificationMapper notificationMapper) {
        this.inboxItemMapper = inboxItemMapper;
        this.notificationMapper = notificationMapper;
    }

    @Override
    public InboxListResponse list(Long userId,
                                  Long cursor,
                                  Integer limit,
                                  Boolean unreadOnly,
                                  Boolean handled,
                                  String category,
                                  Long projectId,
                                  String q) {
        if (userId == null) return new InboxListResponse(0L, 0L, List.of());
        int l = limit == null ? 60 : Math.max(1, Math.min(limit, 200));
        String cat = normalizeCategory(category);

        Long unread = inboxItemMapper.selectCount(
                Wrappers.<UserInboxItem>lambdaQuery()
                        .eq(UserInboxItem::getUserId, userId)
                        .eq(UserInboxItem::getIsHandled, 0)
                        .eq(UserInboxItem::getIsRead, 0)
        );
        Long unhandled = inboxItemMapper.selectCount(
                Wrappers.<UserInboxItem>lambdaQuery()
                        .eq(UserInboxItem::getUserId, userId)
                        .eq(UserInboxItem::getIsHandled, 0)
        );

        List<UserInboxItem> rows = inboxItemMapper.selectList(
                Wrappers.<UserInboxItem>lambdaQuery()
                        .eq(UserInboxItem::getUserId, userId)
                        .eq(Boolean.TRUE.equals(unreadOnly), UserInboxItem::getIsRead, 0)
                        .eq(handled != null, UserInboxItem::getIsHandled, Boolean.TRUE.equals(handled) ? 1 : 0)
                        .eq(cat != null, UserInboxItem::getCategory, cat)
                        .eq(projectId != null, UserInboxItem::getProjectId, projectId)
                        .and(q != null && !q.isBlank(), w -> w
                                .like(UserInboxItem::getTitle, q)
                                .or()
                                .like(UserInboxItem::getContent, q))
                        .lt(cursor != null, UserInboxItem::getId, cursor)
                        .orderByDesc(UserInboxItem::getId)
                        .last("LIMIT " + l)
        );

        List<InboxItem> out = rows.stream()
                .map(r -> new InboxItem(
                        r.getId(),
                        r.getCategory(),
                        r.getTitle(),
                        r.getContent(),
                        r.getProjectId(),
                        r.getTaskId(),
                        r.getCommentId(),
                        r.getNotificationId(),
                        r.getIsRead(),
                        r.getIsHandled(),
                        r.getUpdateTime(),
                        r.getCreateTime()))
                .toList();

        return new InboxListResponse(unread == null ? 0L : unread, unhandled == null ? 0L : unhandled, out);
    }

    @Override
    public int markReadBatch(Long userId, List<Long> ids) {
        if (userId == null || ids == null || ids.isEmpty()) return 0;
        List<Long> list = ids.stream().filter(Objects::nonNull).distinct().limit(200).toList();
        if (list.isEmpty()) return 0;

        LocalDateTime now = LocalDateTime.now();
        int updated = inboxItemMapper.update(
                null,
                Wrappers.<UserInboxItem>lambdaUpdate()
                        .eq(UserInboxItem::getUserId, userId)
                        .in(UserInboxItem::getId, list)
                        .eq(UserInboxItem::getIsRead, 0)
                        .set(UserInboxItem::getIsRead, 1)
                        .set(UserInboxItem::getReadTime, now)
        );

        syncNotificationReadByInboxIds(userId, list, now);
        return updated;
    }

    @Override
    public int markHandledBatch(Long userId, List<Long> ids) {
        if (userId == null || ids == null || ids.isEmpty()) return 0;
        List<Long> list = ids.stream().filter(Objects::nonNull).distinct().limit(200).toList();
        if (list.isEmpty()) return 0;

        LocalDateTime now = LocalDateTime.now();
        int updated = inboxItemMapper.update(
                null,
                Wrappers.<UserInboxItem>lambdaUpdate()
                        .eq(UserInboxItem::getUserId, userId)
                        .in(UserInboxItem::getId, list)
                        .eq(UserInboxItem::getIsHandled, 0)
                        .set(UserInboxItem::getIsHandled, 1)
                        .set(UserInboxItem::getHandledTime, now)
                        .set(UserInboxItem::getIsRead, 1)
                        .set(UserInboxItem::getReadTime, now)
        );

        syncNotificationReadByInboxIds(userId, list, now);
        return updated;
    }

    @Override
    public void upsertFromNotification(Long userId, UserNotification n) {
        if (userId == null || n == null) return;
        String key = dedupKeyForNotification(n);
        if (key == null) return;
        UserInboxItem exists = inboxItemMapper.selectOne(
                Wrappers.<UserInboxItem>lambdaQuery()
                        .eq(UserInboxItem::getUserId, userId)
                        .eq(UserInboxItem::getDedupKey, key)
                        .last("LIMIT 1")
        );

        String cat = categoryByNotificationType(n.getType());
        if (exists != null) {
            exists.setCategory(cat);
            exists.setTitle(n.getTitle());
            exists.setContent(n.getContent());
            exists.setProjectId(n.getProjectId());
            exists.setTaskId(n.getTaskId());
            exists.setCommentId(n.getCommentId());
            exists.setNotificationId(n.getId());
            exists.setIsRead(n.getIsRead() == null ? 0 : n.getIsRead());
            if (exists.getIsRead() != null && exists.getIsRead() == 1 && exists.getReadTime() == null) {
                exists.setReadTime(LocalDateTime.now());
            }
            exists.setIsHandled(0);
            exists.setHandledTime(null);
            inboxItemMapper.updateById(exists);
            return;
        }

        UserInboxItem row = new UserInboxItem();
        row.setUserId(userId);
        row.setDedupKey(key);
        row.setCategory(cat);
        row.setTitle(n.getTitle());
        row.setContent(n.getContent());
        row.setProjectId(n.getProjectId());
        row.setTaskId(n.getTaskId());
        row.setCommentId(n.getCommentId());
        row.setNotificationId(n.getId());
        row.setIsRead(n.getIsRead() == null ? 0 : n.getIsRead());
        if (row.getIsRead() != null && row.getIsRead() == 1) row.setReadTime(LocalDateTime.now());
        row.setIsHandled(0);
        inboxItemMapper.insert(row);
    }

    @Override
    public void upsertAssignedTask(Long userId, Long projectId, Long taskId, String taskTitle) {
        if (userId == null || taskId == null) return;
        String key = "TASK_ASSIGNED:" + taskId;
        UserInboxItem exists = inboxItemMapper.selectOne(
                Wrappers.<UserInboxItem>lambdaQuery()
                        .eq(UserInboxItem::getUserId, userId)
                        .eq(UserInboxItem::getDedupKey, key)
                        .last("LIMIT 1")
        );
        if (exists != null) {
            exists.setCategory("ASSIGNED");
            exists.setTitle("你被分配了任务");
            exists.setContent(taskTitle);
            exists.setProjectId(projectId);
            exists.setTaskId(taskId);
            exists.setIsRead(0);
            exists.setReadTime(null);
            exists.setIsHandled(0);
            exists.setHandledTime(null);
            inboxItemMapper.updateById(exists);
            return;
        }

        UserInboxItem row = new UserInboxItem();
        row.setUserId(userId);
        row.setDedupKey(key);
        row.setCategory("ASSIGNED");
        row.setTitle("你被分配了任务");
        row.setContent(taskTitle);
        row.setProjectId(projectId);
        row.setTaskId(taskId);
        row.setIsRead(0);
        row.setIsHandled(0);
        inboxItemMapper.insert(row);
    }

    private void syncNotificationReadByInboxIds(Long userId, List<Long> inboxIds, LocalDateTime readTime) {
        if (notificationMapper == null) return;
        List<UserInboxItem> rows = inboxItemMapper.selectList(
                Wrappers.<UserInboxItem>lambdaQuery()
                        .eq(UserInboxItem::getUserId, userId)
                        .in(UserInboxItem::getId, inboxIds)
                        .isNotNull(UserInboxItem::getNotificationId)
        );
        if (rows == null || rows.isEmpty()) return;
        List<Long> notifIds = rows.stream()
                .map(UserInboxItem::getNotificationId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(200)
                .toList();
        if (notifIds.isEmpty()) return;
        notificationMapper.update(
                null,
                Wrappers.<UserNotification>lambdaUpdate()
                        .eq(UserNotification::getUserId, userId)
                        .in(UserNotification::getId, notifIds)
                        .eq(UserNotification::getIsRead, 0)
                        .set(UserNotification::getIsRead, 1)
                        .set(UserNotification::getReadTime, readTime == null ? LocalDateTime.now() : readTime)
        );
    }

    private String normalizeCategory(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isBlank()) return null;
        if ("all".equalsIgnoreCase(v)) return null;
        return v;
    }

    private String dedupKeyForNotification(UserNotification n) {
        if (n == null) return null;
        if (n.getGroupKey() != null && !n.getGroupKey().isBlank()) return "NOTIF_GROUP:" + n.getGroupKey();
        if (n.getId() != null) return "NOTIF_ID:" + n.getId();
        return null;
    }

    private String categoryByNotificationType(String type) {
        String t = type == null ? "" : type.trim();
        if (t.equalsIgnoreCase("TASK_MENTION")) return "MENTION";
        if (t.equalsIgnoreCase("TASK_REPLY")) return "REPLY";
        if (t.equalsIgnoreCase("TASK_FOLLOW_UPDATE")) return "FOLLOW";
        if (t.startsWith("PROJECT_")) return "PROJECT";
        if (t.startsWith("TASK_")) return "TASK";
        if (t.equalsIgnoreCase("SYSTEM")) return "SYSTEM";
        return "OTHER";
    }
}

