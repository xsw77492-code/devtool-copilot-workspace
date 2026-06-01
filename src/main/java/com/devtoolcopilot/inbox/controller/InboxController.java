package com.devtoolcopilot.inbox.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.inbox.dto.InboxBatchRequest;
import com.devtoolcopilot.inbox.dto.InboxListResponse;
import com.devtoolcopilot.inbox.service.InboxService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/inbox"})
public class InboxController {
    private final InboxService inboxService;

    public InboxController(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping("/list")
    public R<InboxListResponse> list(@RequestParam(value = "cursor", required = false) Long cursor,
                                     @RequestParam(value = "limit", required = false) Integer limit,
                                     @RequestParam(value = "unreadOnly", required = false) Boolean unreadOnly,
                                     @RequestParam(value = "handled", required = false) Boolean handled,
                                     @RequestParam(value = "category", required = false) String category,
                                     @RequestParam(value = "projectId", required = false) Long projectId,
                                     @RequestParam(value = "q", required = false) String q) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(inboxService.list(userId, cursor, limit, unreadOnly, handled, category, projectId, q));
    }

    @PostMapping("/read-batch")
    public R<Integer> readBatch(@RequestBody InboxBatchRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(inboxService.markReadBatch(userId, req == null ? null : req.getIds()));
    }

    @PostMapping("/handle-batch")
    public R<Integer> handleBatch(@RequestBody InboxBatchRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        return R.ok(inboxService.markHandledBatch(userId, req == null ? null : req.getIds()));
    }
}

