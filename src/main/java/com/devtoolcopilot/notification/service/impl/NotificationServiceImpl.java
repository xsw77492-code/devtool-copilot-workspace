package com.devtoolcopilot.notification.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.devtoolcopilot.inbox.service.InboxService;
import com.devtoolcopilot.notification.dto.NotificationExportResponse;
import com.devtoolcopilot.notification.dto.NotificationItem;
import com.devtoolcopilot.notification.dto.NotificationListResponse;
import com.devtoolcopilot.notification.dto.NotificationSettingsRequest;
import com.devtoolcopilot.notification.dto.NotificationSettingsResponse;
import com.devtoolcopilot.notification.dto.NotificationTypePrefItem;
import com.devtoolcopilot.notification.entity.UserNotification;
import com.devtoolcopilot.notification.entity.UserNotificationSetting;
import com.devtoolcopilot.notification.entity.UserNotificationTypePref;
import com.devtoolcopilot.notification.mapper.UserNotificationMapper;
import com.devtoolcopilot.notification.mapper.UserNotificationSettingMapper;
import com.devtoolcopilot.notification.mapper.UserNotificationTypePrefMapper;
import com.devtoolcopilot.notification.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class NotificationServiceImpl implements NotificationService {
    private final UserNotificationMapper notificationMapper;
    private final UserNotificationSettingMapper settingMapper;
    private final UserNotificationTypePrefMapper typePrefMapper;
    private final ObjectMapper objectMapper;
    private final InboxService inboxService;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService keepAliveScheduler = Executors.newSingleThreadScheduledExecutor();

    public NotificationServiceImpl(UserNotificationMapper notificationMapper,
                                   UserNotificationSettingMapper settingMapper,
                                   UserNotificationTypePrefMapper typePrefMapper,
                                   ObjectMapper objectMapper,
                                   InboxService inboxService) {
        this.notificationMapper = notificationMapper;
        this.settingMapper = settingMapper;
        this.typePrefMapper = typePrefMapper;
        this.objectMapper = objectMapper;
        this.inboxService = inboxService;
        keepAliveScheduler.scheduleAtFixedRate(this::keepAliveAll, 25, 25, TimeUnit.SECONDS);
    }

    @Override
    public UserNotification create(Long userId, String type, String title, String content, String dataJson) {
        return create(userId, type, title, content, dataJson, null, null, null, null);
    }

    @Override
    public UserNotification create(Long userId,
                                   String type,
                                   String title,
                                   String content,
                                   String dataJson,
                                   Long projectId,
                                   Long taskId,
                                   Long commentId,
                                   String groupKey) {
        if (userId == null) return null;
        String t = type == null ? "SYSTEM" : type;
        if (!isTypeEnabled(userId, t)) return null;
        UserNotification n = new UserNotification();
        n.setUserId(userId);
        n.setType(t);
        n.setTitle(title == null ? "" : title);
        n.setContent(content);
        n.setDataJson(dataJson);
        n.setIsRead(0);
        n.setProjectId(projectId);
        n.setTaskId(taskId);
        n.setCommentId(commentId);
        n.setGroupKey(groupKey);
        n.setAggCount(1);

        boolean muted = isMutedNow(userId);
        if (groupKey != null && !groupKey.isBlank()) {
            UserNotification existing = notificationMapper.selectOne(
                    Wrappers.<UserNotification>lambdaQuery()
                            .eq(UserNotification::getUserId, userId)
                            .eq(UserNotification::getGroupKey, groupKey)
                            .eq(UserNotification::getIsRead, 0)
                            .orderByDesc(UserNotification::getId)
                            .last("LIMIT 1")
            );
            if (existing != null) {
                existing.setAggCount(Math.max(1, (existing.getAggCount() == null ? 1 : existing.getAggCount())) + 1);
                if (content != null && !content.isBlank()) existing.setContent(content);
                if (dataJson != null && !dataJson.isBlank()) existing.setDataJson(dataJson);
                if (projectId != null) existing.setProjectId(projectId);
                if (taskId != null) existing.setTaskId(taskId);
                if (commentId != null) existing.setCommentId(commentId);
                notificationMapper.updateById(existing);
                try {
                    if (inboxService != null) inboxService.upsertFromNotification(userId, existing);
                } catch (Exception ignored) {
                }
                if (!muted) pushUpdate(userId, existing);
                return existing;
            }
        }

        notificationMapper.insert(n);
        try {
            if (inboxService != null) inboxService.upsertFromNotification(userId, n);
        } catch (Exception ignored) {
        }
        if (!muted) pushNew(userId, n);
        return n;
    }

    @Override
    public NotificationListResponse list(Long userId, Long cursor, Integer limit, Boolean unreadOnly) {
        return list(userId, cursor, limit, unreadOnly, List.of(), null, null);
    }

    @Override
    public NotificationListResponse list(Long userId,
                                         Long cursor,
                                         Integer limit,
                                         Boolean unreadOnly,
                                         String type,
                                         Long projectId,
                                         String q) {
        if (type == null || type.isBlank()) {
            return list(userId, cursor, limit, unreadOnly, List.of(), projectId, q);
        }
        return list(userId, cursor, limit, unreadOnly, List.of(type), projectId, q);
    }

    @Override
    public NotificationListResponse list(Long userId,
                                         Long cursor,
                                         Integer limit,
                                         Boolean unreadOnly,
                                         List<String> types,
                                         Long projectId,
                                         String q) {
        int l = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        Long unread = unreadCount(userId);
        List<String> ts = normalizeTypes(types);
        List<UserNotification> rows = notificationMapper.selectList(
                Wrappers.<UserNotification>lambdaQuery()
                        .eq(UserNotification::getUserId, userId)
                        .eq(Boolean.TRUE.equals(unreadOnly), UserNotification::getIsRead, 0)
                        .eq(projectId != null, UserNotification::getProjectId, projectId)
                        .in(ts != null && !ts.isEmpty(), UserNotification::getType, ts)
                        .and(q != null && !q.isBlank(), w -> w
                                .like(UserNotification::getTitle, q)
                                .or()
                                .like(UserNotification::getContent, q))
                        .lt(cursor != null, UserNotification::getId, cursor)
                        .orderByDesc(UserNotification::getId)
                        .last("LIMIT " + l)
        );
        List<NotificationItem> items = rows.stream()
                .map(r -> new NotificationItem(
                        r.getId(),
                        r.getProjectId(),
                        r.getTaskId(),
                        r.getCommentId(),
                        r.getType(),
                        r.getTitle(),
                        r.getContent(),
                        r.getDataJson(),
                        r.getIsRead(),
                        r.getAggCount(),
                        r.getUpdateTime(),
                        r.getCreateTime()))
                .toList();
        return new NotificationListResponse(unread, items);
    }

    @Override
    public Long unreadCount(Long userId) {
        if (userId == null) return 0L;
        Long cnt = notificationMapper.selectCount(
                Wrappers.<UserNotification>lambdaQuery()
                        .eq(UserNotification::getUserId, userId)
                        .eq(UserNotification::getIsRead, 0)
        );
        return cnt == null ? 0L : cnt;
    }

    @Override
    public boolean markRead(Long userId, Long id) {
        if (userId == null || id == null) return false;
        UserNotification n = notificationMapper.selectById(id);
        if (n == null || !userId.equals(n.getUserId())) return false;
        if (n.getIsRead() != null && n.getIsRead() == 1) return true;
        n.setIsRead(1);
        n.setReadTime(LocalDateTime.now());
        notificationMapper.updateById(n);
        return true;
    }

    @Override
    public int markReadBatch(Long userId, List<Long> ids) {
        if (userId == null || ids == null || ids.isEmpty()) return 0;
        List<Long> list = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(200)
                .toList();
        if (list.isEmpty()) return 0;
        return notificationMapper.update(
                null,
                Wrappers.<UserNotification>lambdaUpdate()
                        .eq(UserNotification::getUserId, userId)
                        .in(UserNotification::getId, list)
                        .eq(UserNotification::getIsRead, 0)
                        .set(UserNotification::getIsRead, 1)
                        .set(UserNotification::getReadTime, LocalDateTime.now())
        );
    }

    @Override
    public int markAllRead(Long userId) {
        if (userId == null) return 0;
        return notificationMapper.update(
                null,
                Wrappers.<UserNotification>lambdaUpdate()
                        .eq(UserNotification::getUserId, userId)
                        .eq(UserNotification::getIsRead, 0)
                        .set(UserNotification::getIsRead, 1)
                        .set(UserNotification::getReadTime, LocalDateTime.now())
        );
    }

    @Override
    public int markReadByFilter(Long userId, Boolean unreadOnly, String type, Long projectId, String q) {
        if (type == null || type.isBlank()) {
            return markReadByFilter(userId, unreadOnly, (List<String>) null, projectId, q);
        }
        return markReadByFilter(userId, unreadOnly, List.of(type), projectId, q);
    }

    @Override
    public int markReadByFilter(Long userId, Boolean unreadOnly, List<String> types, Long projectId, String q) {
        if (userId == null) return 0;
        List<String> ts = normalizeTypes(types);
        var u = Wrappers.<UserNotification>lambdaUpdate().eq(UserNotification::getUserId, userId);
        if (Boolean.TRUE.equals(unreadOnly)) u.eq(UserNotification::getIsRead, 0);
        if (ts != null && !ts.isEmpty()) u.in(UserNotification::getType, ts);
        if (projectId != null) u.eq(UserNotification::getProjectId, projectId);
        if (q != null && !q.isBlank()) {
            u.and(w -> w.like(UserNotification::getTitle, q).or().like(UserNotification::getContent, q));
        }
        u.set(UserNotification::getIsRead, 1).set(UserNotification::getReadTime, LocalDateTime.now());
        return notificationMapper.update(null, u);
    }

    @Override
    public boolean deleteOne(Long userId, Long id) {
        if (userId == null || id == null) return false;
        UserNotification n = notificationMapper.selectById(id);
        if (n == null || !userId.equals(n.getUserId())) return false;
        return notificationMapper.deleteById(id) > 0;
    }

    @Override
    public int clearRead(Long userId) {
        if (userId == null) return 0;
        return notificationMapper.delete(
                Wrappers.<UserNotification>lambdaQuery()
                        .eq(UserNotification::getUserId, userId)
                        .eq(UserNotification::getIsRead, 1)
        );
    }

    @Override
    public NotificationExportResponse exportCsv(Long userId, Boolean unreadOnly, String type, Long projectId, String q) {
        NotificationListResponse resp = list(userId, null, 200, unreadOnly, type, projectId, q);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        StringBuilder sb = new StringBuilder();
        sb.append("id,createTime,updateTime,type,title,content,isRead,aggCount,projectId,taskId,commentId\n");
        for (NotificationItem it : resp.getList()) {
            String ct = it.getCreateTime() == null ? "" : fmt.format(it.getCreateTime());
            String ut = it.getUpdateTime() == null ? "" : fmt.format(it.getUpdateTime());
            sb.append(csv(String.valueOf(it.getId()))).append(',')
                    .append(csv(ct)).append(',')
                    .append(csv(ut)).append(',')
                    .append(csv(it.getType())).append(',')
                    .append(csv(it.getTitle())).append(',')
                    .append(csv(it.getContent())).append(',')
                    .append(csv(String.valueOf(it.getIsRead()))).append(',')
                    .append(csv(String.valueOf(it.getAggCount() == null ? 1 : it.getAggCount()))).append(',')
                    .append(csv(it.getProjectId() == null ? "" : String.valueOf(it.getProjectId()))).append(',')
                    .append(csv(it.getTaskId() == null ? "" : String.valueOf(it.getTaskId()))).append(',')
                    .append(csv(it.getCommentId() == null ? "" : String.valueOf(it.getCommentId())))
                    .append('\n');
        }
        String name = "notifications.csv";
        return new NotificationExportResponse(name, sb.toString());
    }

    @Override
    public NotificationExportResponse exportCsv(Long userId, Boolean unreadOnly, List<String> types, Long projectId, String q) {
        NotificationListResponse resp = list(userId, null, 200, unreadOnly, types, projectId, q);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        StringBuilder sb = new StringBuilder();
        sb.append("id,createTime,updateTime,type,title,content,isRead,aggCount,projectId,taskId,commentId\n");
        for (NotificationItem it : resp.getList()) {
            String ct = it.getCreateTime() == null ? "" : fmt.format(it.getCreateTime());
            String ut = it.getUpdateTime() == null ? "" : fmt.format(it.getUpdateTime());
            sb.append(csv(String.valueOf(it.getId()))).append(',')
                    .append(csv(ct)).append(',')
                    .append(csv(ut)).append(',')
                    .append(csv(it.getType())).append(',')
                    .append(csv(it.getTitle())).append(',')
                    .append(csv(it.getContent())).append(',')
                    .append(csv(String.valueOf(it.getIsRead()))).append(',')
                    .append(csv(String.valueOf(it.getAggCount() == null ? 1 : it.getAggCount()))).append(',')
                    .append(csv(it.getProjectId() == null ? "" : String.valueOf(it.getProjectId()))).append(',')
                    .append(csv(it.getTaskId() == null ? "" : String.valueOf(it.getTaskId()))).append(',')
                    .append(csv(it.getCommentId() == null ? "" : String.valueOf(it.getCommentId())))
                    .append('\n');
        }
        String name = "notifications.csv";
        return new NotificationExportResponse(name, sb.toString());
    }

    @Override
    public NotificationSettingsResponse getSettings(Long userId) {
        if (userId == null) return new NotificationSettingsResponse(0, 0, 0, List.of());
        UserNotificationSetting s = settingMapper.selectById(userId);
        int dndEnabled = s == null || s.getDndEnabled() == null ? 0 : s.getDndEnabled();
        int start = s == null || s.getDndStartMinute() == null ? 0 : s.getDndStartMinute();
        int end = s == null || s.getDndEndMinute() == null ? 0 : s.getDndEndMinute();
        List<UserNotificationTypePref> rows = typePrefMapper.selectList(
                Wrappers.<UserNotificationTypePref>lambdaQuery()
                        .eq(UserNotificationTypePref::getUserId, userId)
                        .orderByDesc(UserNotificationTypePref::getUpdateTime)
                        .last("LIMIT 200")
        );
        List<NotificationTypePrefItem> prefs = rows.stream()
                .map(r -> new NotificationTypePrefItem(r.getType(), r.getEnabled()))
                .toList();
        return new NotificationSettingsResponse(dndEnabled, start, end, prefs);
    }

    @Override
    public void updateSettings(Long userId, NotificationSettingsRequest settings) {
        if (userId == null || settings == null) return;
        UserNotificationSetting s = settingMapper.selectById(userId);
        if (s == null) {
            s = new UserNotificationSetting();
            s.setUserId(userId);
            s.setDndEnabled(0);
            s.setDndStartMinute(0);
            s.setDndEndMinute(0);
            settingMapper.insert(s);
        }
        if (settings.getDndEnabled() != null) s.setDndEnabled(settings.getDndEnabled());
        if (settings.getDndStartMinute() != null) s.setDndStartMinute(settings.getDndStartMinute());
        if (settings.getDndEndMinute() != null) s.setDndEndMinute(settings.getDndEndMinute());
        settingMapper.updateById(s);

        if (settings.getTypePrefs() != null) {
            for (NotificationTypePrefItem it : settings.getTypePrefs()) {
                if (it == null || it.getType() == null || it.getType().isBlank()) continue;
                UserNotificationTypePref p = typePrefMapper.selectOne(
                        Wrappers.<UserNotificationTypePref>lambdaQuery()
                                .eq(UserNotificationTypePref::getUserId, userId)
                                .eq(UserNotificationTypePref::getType, it.getType())
                                .last("LIMIT 1")
                );
                if (p == null) {
                    p = new UserNotificationTypePref();
                    p.setUserId(userId);
                    p.setType(it.getType());
                    p.setEnabled(it.getEnabled() == null ? 1 : it.getEnabled());
                    typePrefMapper.insert(p);
                } else {
                    p.setEnabled(it.getEnabled() == null ? 1 : it.getEnabled());
                    typePrefMapper.updateById(p);
                }
            }
        }
    }

    @Override
    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        if (userId == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("未登录"));
            } catch (Exception ignored) {
            } finally {
                emitter.complete();
            }
            return emitter;
        }

        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError((e) -> removeEmitter(userId, emitter));

        try {
            emitter.send(SseEmitter.event().name("ready").data("1"));
        } catch (Exception ignored) {
        }
        return emitter;
    }

    private void push(Long userId, UserNotification n) {
        pushNew(userId, n);
    }

    private void pushNew(Long userId, UserNotification n) {
        pushEvent(userId, "notification", n);
    }

    private void pushUpdate(Long userId, UserNotification n) {
        pushEvent(userId, "notification-update", n);
    }

    private void pushEvent(Long userId, String event, UserNotification n) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) return;
        NotificationItem item = new NotificationItem(
                n.getId(),
                n.getProjectId(),
                n.getTaskId(),
                n.getCommentId(),
                n.getType(),
                n.getTitle(),
                n.getContent(),
                n.getDataJson(),
                n.getIsRead(),
                n.getAggCount(),
                n.getUpdateTime(),
                n.getCreateTime());
        String data;
        try {
            data = objectMapper.writeValueAsString(item);
        } catch (Exception ex) {
            return;
        }
        for (SseEmitter e : list) {
            try {
                e.send(SseEmitter.event().name(event).data(data));
            } catch (Exception ex) {
                removeEmitter(userId, e);
            }
        }
    }

    private void keepAliveAll() {
        for (Map.Entry<Long, CopyOnWriteArrayList<SseEmitter>> en : emitters.entrySet()) {
            Long userId = en.getKey();
            for (SseEmitter e : en.getValue()) {
                try {
                    e.send(SseEmitter.event().name("ping").data("1"));
                } catch (Exception ex) {
                    removeEmitter(userId, e);
                }
            }
        }
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(userId);
        if (list == null) return;
        list.remove(emitter);
        if (list.isEmpty()) emitters.remove(userId);
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + safe(s) + "\"";
    }

    private boolean isTypeEnabled(Long userId, String type) {
        if (userId == null || type == null || type.isBlank()) return true;
        UserNotificationTypePref p = typePrefMapper.selectOne(
                Wrappers.<UserNotificationTypePref>lambdaQuery()
                        .eq(UserNotificationTypePref::getUserId, userId)
                        .eq(UserNotificationTypePref::getType, type)
                        .last("LIMIT 1")
        );
        if (p == null || p.getEnabled() == null) return true;
        return p.getEnabled() == 1;
    }

    private boolean isMutedNow(Long userId) {
        if (userId == null) return false;
        UserNotificationSetting s = settingMapper.selectById(userId);
        if (s == null || s.getDndEnabled() == null || s.getDndEnabled() != 1) return false;
        int start = s.getDndStartMinute() == null ? 0 : s.getDndStartMinute();
        int end = s.getDndEndMinute() == null ? 0 : s.getDndEndMinute();
        int now = LocalDateTime.now().getHour() * 60 + LocalDateTime.now().getMinute();
        if (start == end) return true;
        if (start < end) return now >= start && now < end;
        return now >= start || now < end;
    }

    private static String csv(String s) {
        if (s == null) return "";
        String v = s.replace("\"", "\"\"");
        if (v.contains(",") || v.contains("\n") || v.contains("\r")) return "\"" + v + "\"";
        return v;
    }

    private static List<String> normalizeTypes(List<String> types) {
        if (types == null || types.isEmpty()) return null;
        List<String> out = new ArrayList<>();
        for (String t : types) {
            if (t == null) continue;
            String v = t.trim();
            if (v.isBlank()) continue;
            if (!out.contains(v)) out.add(v);
            if (out.size() >= 50) break;
        }
        return out.isEmpty() ? null : out;
    }
}
