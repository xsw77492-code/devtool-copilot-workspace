package com.devtoolcopilot.notification.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.notification.dto.NotificationExportResponse;
import com.devtoolcopilot.notification.dto.NotificationListResponse;
import com.devtoolcopilot.notification.dto.NotificationReadBatchRequest;
import com.devtoolcopilot.notification.dto.NotificationSettingsRequest;
import com.devtoolcopilot.notification.dto.NotificationSettingsResponse;
import com.devtoolcopilot.notification.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping({"/api/notification", "/api/notifications"})
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/list")
    public R<NotificationListResponse> list(@RequestParam(value = "cursor", required = false) Long cursor,
                                           @RequestParam(value = "limit", required = false) Integer limit,
                                           @RequestParam(value = "unreadOnly", required = false) Boolean unreadOnly,
                                           @RequestParam(value = "type", required = false) String type,
                                           @RequestParam(value = "types", required = false) String types,
                                           @RequestParam(value = "projectId", required = false) Long projectId,
                                           @RequestParam(value = "q", required = false) String q) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        List<String> ts = splitCsv(types);
        if (ts != null && !ts.isEmpty()) {
            return R.ok(notificationService.list(userId, cursor, limit, unreadOnly, ts, projectId, q));
        }
        return R.ok(notificationService.list(userId, cursor, limit, unreadOnly, type, projectId, q));
    }

    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(notificationService.unreadCount(userId));
    }

    @PostMapping("/read/{id}")
    public R<Void> read(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        boolean ok = notificationService.markRead(userId, id);
        if (!ok) return R.fail(404, "通知不存在");
        return R.ok();
    }

    @PostMapping("/read-batch")
    public R<Integer> readBatch(@RequestBody NotificationReadBatchRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(notificationService.markReadBatch(userId, req == null ? null : req.getIds()));
    }

    @PostMapping("/read-all")
    public R<Integer> readAll() {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(notificationService.markAllRead(userId));
    }

    @PostMapping("/read-by-filter")
    public R<Integer> readByFilter(@RequestParam(value = "unreadOnly", required = false) Boolean unreadOnly,
                                   @RequestParam(value = "type", required = false) String type,
                                   @RequestParam(value = "types", required = false) String types,
                                   @RequestParam(value = "projectId", required = false) Long projectId,
                                   @RequestParam(value = "q", required = false) String q) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        List<String> ts = splitCsv(types);
        if (ts != null && !ts.isEmpty()) {
            return R.ok(notificationService.markReadByFilter(userId, unreadOnly, ts, projectId, q));
        }
        return R.ok(notificationService.markReadByFilter(userId, unreadOnly, type, projectId, q));
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteOne(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        boolean ok = notificationService.deleteOne(userId, id);
        if (!ok) return R.fail(404, "通知不存在");
        return R.ok();
    }

    @PostMapping("/clear-read")
    public R<Integer> clearRead() {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(notificationService.clearRead(userId));
    }

    @GetMapping("/export")
    public R<NotificationExportResponse> export(@RequestParam(value = "unreadOnly", required = false) Boolean unreadOnly,
                                                @RequestParam(value = "type", required = false) String type,
                                                @RequestParam(value = "types", required = false) String types,
                                                @RequestParam(value = "projectId", required = false) Long projectId,
                                                @RequestParam(value = "q", required = false) String q) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        List<String> ts = splitCsv(types);
        if (ts != null && !ts.isEmpty()) {
            return R.ok(notificationService.exportCsv(userId, unreadOnly, ts, projectId, q));
        }
        return R.ok(notificationService.exportCsv(userId, unreadOnly, type, projectId, q));
    }

    @GetMapping("/settings")
    public R<NotificationSettingsResponse> settings() {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(notificationService.getSettings(userId));
    }

    @PostMapping("/settings")
    public R<Void> updateSettings(@RequestBody NotificationSettingsRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        notificationService.updateSettings(userId, req);
        return R.ok();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return notificationService.connect(userId);
    }

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] arr = raw.split(",");
        if (arr.length == 0) return null;
        return java.util.Arrays.stream(arr)
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isBlank())
                .distinct()
                .limit(50)
                .toList();
    }
}
