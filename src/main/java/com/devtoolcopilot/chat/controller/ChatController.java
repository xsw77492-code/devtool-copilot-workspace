package com.devtoolcopilot.chat.controller;

import com.devtoolcopilot.chat.dto.ChatAttachmentItem;
import com.devtoolcopilot.chat.dto.ChatConversationDetail;
import com.devtoolcopilot.chat.dto.ChatConversationItem;
import com.devtoolcopilot.chat.dto.ChatFileItem;
import com.devtoolcopilot.chat.dto.ChatMemberItem;
import com.devtoolcopilot.chat.dto.ChatMessageItem;
import com.devtoolcopilot.chat.service.ChatService;
import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.common.exception.ApiException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // ── 会话 ────────────────────────────────────────────────

    @GetMapping("/conversations")
    public R<List<ChatConversationItem>> conversations() {
        return R.ok(chatService.listConversations(UserContext.getUserId()));
    }

    @PostMapping("/conversations/single")
    public R<ChatConversationItem> createSingle(@RequestBody CreateSingleReq req) {
        return R.ok(chatService.createOrGetSingle(UserContext.getUserId(), req.targetUserId()));
    }

    @PostMapping("/conversations/group")
    public R<ChatConversationItem> createGroup(@RequestBody CreateGroupReq req) {
        return R.ok(chatService.createGroup(UserContext.getUserId(), req.name(), req.memberIds()));
    }

    @PatchMapping("/conversations/{id}")
    public R<Void> rename(@PathVariable Long id, @RequestBody RenameReq req) {
        chatService.rename(UserContext.getUserId(), id, req.name());
        return R.ok();
    }

    @DeleteMapping("/conversations/{id}")
    public R<Void> dissolve(@PathVariable Long id) {
        chatService.dissolve(UserContext.getUserId(), id);
        return R.ok();
    }

    // ── 消息 ────────────────────────────────────────────────

    @GetMapping("/conversations/{id}/messages")
    public R<List<ChatMessageItem>> messages(@PathVariable Long id,
                                             @RequestParam(required = false) Long beforeId,
                                             @RequestParam(defaultValue = "30") int limit) {
        return R.ok(chatService.listMessages(UserContext.getUserId(), id, beforeId, limit));
    }

    @PostMapping("/conversations/{id}/messages")
    public R<ChatMessageItem> send(@PathVariable Long id, @RequestBody SendMessageReq req) {
        return R.ok(chatService.sendMessage(UserContext.getUserId(), id, req.content(), req.msgType(), req.attachmentId(), req.replyToId()));
    }

    @GetMapping("/conversations/{id}/messages/search")
    public R<List<ChatMessageItem>> search(@PathVariable Long id,
                                           @RequestParam String q,
                                           @RequestParam(defaultValue = "30") int limit) {
        return R.ok(chatService.searchMessages(UserContext.getUserId(), id, q, limit));
    }

    @GetMapping("/conversations/{id}/messages/{messageId}/readers")
    public R<List<Long>> readers(@PathVariable Long id, @PathVariable Long messageId) {
        return R.ok(chatService.readUserIds(UserContext.getUserId(), id, messageId));
    }

    @PostMapping("/messages/{id}/recall")
    public R<ChatMessageItem> recall(@PathVariable Long id) {
        return R.ok(chatService.recallMessage(UserContext.getUserId(), id));
    }

    @PostMapping("/messages/{id}/forward")
    public R<ChatMessageItem> forward(@PathVariable Long id, @RequestBody ForwardReq req) {
        return R.ok(chatService.forwardMessage(UserContext.getUserId(), id, req.targetConversationId()));
    }

    @PostMapping("/conversations/{id}/read")
    public R<ChatMessageItem> read(@PathVariable Long id) {
        return R.ok(chatService.markRead(UserContext.getUserId(), id));
    }

    // ── 群成员管理 ─────────────────────────────────────────

    @GetMapping("/members")
    public R<List<ChatMemberItem>> members() {
        return R.ok(chatService.listAvailableMembers(UserContext.getUserId()));
    }

    @GetMapping("/conversations/{id}/members")
    public R<List<ChatMemberItem>> conversationMembers(@PathVariable Long id) {
        return R.ok(chatService.listConversationMembers(UserContext.getUserId(), id));
    }

    @PostMapping("/conversations/{id}/members")
    public R<Void> addMembers(@PathVariable Long id, @RequestBody AddMembersReq req) {
        chatService.addMembers(UserContext.getUserId(), id, req.userIds());
        return R.ok();
    }

    @DeleteMapping("/conversations/{id}/members/{userId}")
    public R<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        chatService.removeMember(UserContext.getUserId(), id, userId);
        return R.ok();
    }

    // ── 会话详情 / 设置 ─────────────────────────────────────

    @GetMapping("/conversations/{id}/detail")
    public R<ChatConversationDetail> detail(@PathVariable Long id) {
        return R.ok(chatService.conversationDetail(UserContext.getUserId(), id));
    }

    @PutMapping("/conversations/{id}/announcement")
    public R<Void> announcement(@PathVariable Long id, @RequestBody AnnouncementReq req) {
        chatService.updateAnnouncement(UserContext.getUserId(), id, req.announcement());
        return R.ok();
    }

    @GetMapping("/conversations/{id}/files")
    public R<List<ChatFileItem>> files(@PathVariable Long id,
                                       @RequestParam(required = false) String type) {
        return R.ok(chatService.listSharedFiles(UserContext.getUserId(), id, type));
    }

    @PatchMapping("/conversations/{id}/settings")
    public R<Void> settings(@PathVariable Long id, @RequestBody MutedReq req) {
        chatService.updateMuted(UserContext.getUserId(), id, Boolean.TRUE.equals(req.muted()));
        return R.ok();
    }

    @PutMapping("/conversations/{id}/pin")
    public R<Void> pin(@PathVariable Long id) {
        chatService.updatePinned(UserContext.getUserId(), id, true);
        return R.ok();
    }

    @DeleteMapping("/conversations/{id}/pin")
    public R<Void> unpin(@PathVariable Long id) {
        chatService.updatePinned(UserContext.getUserId(), id, false);
        return R.ok();
    }

    @PostMapping("/conversations/{id}/unread")
    public R<Void> unread(@PathVariable Long id) {
        chatService.markUnread(UserContext.getUserId(), id);
        return R.ok();
    }

    // ── 附件 ────────────────────────────────────────────────

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ChatAttachmentItem> upload(@RequestPart("file") MultipartFile file) {
        return R.ok(chatService.uploadAttachment(UserContext.getUserId(), file));
    }

    @GetMapping("/attachments/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable Long id) {
        return buildFile(chatService.loadAttachment(UserContext.getUserId(), id, false, true));
    }

    @GetMapping("/attachments/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long id) {
        return buildFile(chatService.loadAttachment(UserContext.getUserId(), id, true, false));
    }

    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        return buildFile(chatService.loadAttachment(UserContext.getUserId(), id, false, false));
    }

    // ── 请求体 ──────────────────────────────────────────────

    public record CreateSingleReq(Long targetUserId) {
    }

    public record CreateGroupReq(String name, List<Long> memberIds) {
    }

    public record RenameReq(String name) {
    }

    public record SendMessageReq(String content, String msgType, Long attachmentId, Long replyToId) {
    }

    public record ForwardReq(Long targetConversationId) {
    }

    public record AddMembersReq(List<Long> userIds) {
    }

    public record AnnouncementReq(String announcement) {
    }

    public record MutedReq(Boolean muted) {
    }

    // ── 工具 ────────────────────────────────────────────────

    private static ResponseEntity<Resource> buildFile(ChatService.DownloadFile f) {
        if (f == null) throw new ApiException(404, "文件不存在");
        FileSystemResource res = new FileSystemResource(f.path());
        String name = f.filename() == null ? "file" : f.filename();
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
        boolean image = f.contentType() != null && f.contentType().toLowerCase().startsWith("image/");
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                (image ? "inline" : "attachment") + "; filename*=UTF-8''" + encoded);
        headers.add(HttpHeaders.CACHE_CONTROL, "private, max-age=0, no-store");
        long len = -1L;
        try {
            len = res.contentLength();
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(f.contentType()))
                .contentLength(len)
                .body(res);
    }
}
