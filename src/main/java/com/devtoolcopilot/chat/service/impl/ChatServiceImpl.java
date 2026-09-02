package com.devtoolcopilot.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.attachment.config.AttachmentProperties;
import com.devtoolcopilot.chat.dto.ChatAttachmentItem;
import com.devtoolcopilot.chat.dto.ChatConversationDetail;
import com.devtoolcopilot.chat.dto.ChatConversationItem;
import com.devtoolcopilot.chat.dto.ChatFileItem;
import com.devtoolcopilot.chat.dto.ChatMemberItem;
import com.devtoolcopilot.chat.dto.ChatMessageItem;
import com.devtoolcopilot.chat.dto.ChatSenderItem;
import com.devtoolcopilot.chat.entity.ChatAttachment;
import com.devtoolcopilot.chat.entity.ChatConversation;
import com.devtoolcopilot.chat.entity.ChatConversationMember;
import com.devtoolcopilot.chat.entity.ChatMessage;
import com.devtoolcopilot.chat.mapper.ChatAttachmentMapper;
import com.devtoolcopilot.chat.mapper.ChatConversationMapper;
import com.devtoolcopilot.chat.mapper.ChatConversationMemberMapper;
import com.devtoolcopilot.chat.mapper.ChatMessageMapper;
import com.devtoolcopilot.chat.service.ChatService;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.realtime.service.RealtimeCollabService;
import com.devtoolcopilot.user.entity.User;
import com.devtoolcopilot.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {
    private static final Set<String> TEXT_TYPES = Set.of("TEXT", "IMAGE", "FILE");

    private final ChatConversationMapper conversationMapper;
    private final ChatConversationMemberMapper memberMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatAttachmentMapper attachmentMapper;
    private final UserMapper userMapper;
    private final RealtimeCollabService realtimeCollabService;
    private final AttachmentProperties attachmentProperties;

    public ChatServiceImpl(ChatConversationMapper conversationMapper,
                           ChatConversationMemberMapper memberMapper,
                           ChatMessageMapper messageMapper,
                           ChatAttachmentMapper attachmentMapper,
                           UserMapper userMapper,
                           RealtimeCollabService realtimeCollabService,
                           AttachmentProperties attachmentProperties) {
        this.conversationMapper = conversationMapper;
        this.memberMapper = memberMapper;
        this.messageMapper = messageMapper;
        this.attachmentMapper = attachmentMapper;
        this.userMapper = userMapper;
        this.realtimeCollabService = realtimeCollabService;
        this.attachmentProperties = attachmentProperties;
    }

    // ── 会话列表 ─────────────────────────────────────────────

    @Override
    public List<ChatConversationItem> listConversations(Long userId) {
        requireLogin(userId);
        List<ChatConversationMember> my = memberMapper.selectList(
                Wrappers.<ChatConversationMember>lambdaQuery().eq(ChatConversationMember::getUserId, userId));
        if (my.isEmpty()) return List.of();
        List<Long> convIds = my.stream().map(ChatConversationMember::getConversationId).distinct().toList();
        Map<Long, ChatConversation> convMap = conversationMapper.selectBatchIds(convIds).stream()
                .collect(Collectors.toMap(ChatConversation::getId, Function.identity()));
        Map<Long, ChatConversationMember> myByConv = my.stream()
                .collect(Collectors.toMap(ChatConversationMember::getConversationId, Function.identity(), (a, b) -> a));

        List<ChatConversationMember> allMembers = memberMapper.selectList(
                Wrappers.<ChatConversationMember>lambdaQuery().in(ChatConversationMember::getConversationId, convIds));
        Map<Long, Long> countByConv = allMembers.stream().collect(
                Collectors.groupingBy(ChatConversationMember::getConversationId, Collectors.counting()));
        Map<Long, List<ChatConversationMember>> membersByConv = allMembers.stream()
                .collect(Collectors.groupingBy(ChatConversationMember::getConversationId));
        Set<Long> allUserIds = allMembers.stream().map(ChatConversationMember::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = loadUsers(allUserIds);

        // 最近消息：一次拉取最近记录，按会话去重取最新一条
        Map<Long, ChatMessage> lastByConv = new HashMap<>();
        List<ChatMessage> recent = messageMapper.selectList(
                Wrappers.<ChatMessage>lambdaQuery()
                        .in(ChatMessage::getConversationId, convIds)
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT 2000"));
        for (ChatMessage m : recent) {
            lastByConv.putIfAbsent(m.getConversationId(), m);
        }

        List<ChatConversationItem> items = new ArrayList<>();
        for (ChatConversation c : convMap.values()) {
            ChatConversationMember mine = myByConv.get(c.getId());
            ChatMessage last = lastByConv.get(c.getId());
            long lastRead = mine == null || mine.getLastReadMessageId() == null ? 0L : mine.getLastReadMessageId();
            long unread = last == null || last.getId() <= lastRead ? 0L : countUnread(c.getId(), lastRead);
            ChatSenderItem other = otherUser(userId, c, membersByConv.getOrDefault(c.getId(), List.of()), userMap);
            String display = "SINGLE".equals(c.getType())
                    ? (other != null && notBlank(other.getNickname()) ? other.getNickname() : other != null ? other.getUsername() : "会话")
                    : notBlank(c.getName()) ? c.getName() : "群聊";
            ChatMessageItem lastItem = last == null ? null : toMessageItem(last, userMap);
            ChatConversationItem item = new ChatConversationItem(
                    c.getId(), c.getType(), display, c.getCreatorId(),
                    countByConv.getOrDefault(c.getId(), 0L),
                    mine == null ? "MEMBER" : mine.getRole(),
                    unread, lastItem, other, c.getCreateTime());
            item.setPinned(mine != null && Integer.valueOf(1).equals(mine.getPinned()));
            item.setMuted(mine != null && Integer.valueOf(1).equals(mine.getMuted()));
            if ("SINGLE".equals(c.getType())) {
                item.setPeerReadMessageId(peerReadMessageId(c.getId(), userId, allMembers));
            }
            items.add(item);
        }
        items.sort((a, b) -> {
            boolean pa = Boolean.TRUE.equals(a.getPinned());
            boolean pb = Boolean.TRUE.equals(b.getPinned());
            if (pa != pb) return pa ? -1 : 1;
            LocalDateTime ta = a.getLastMessage() == null ? null : a.getLastMessage().getCreateTime();
            LocalDateTime tb = b.getLastMessage() == null ? null : b.getLastMessage().getCreateTime();
            if (ta == null && tb == null) return b.getCreateTime().compareTo(a.getCreateTime());
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
        return items;
    }

    // ── 单聊 ─────────────────────────────────────────────────

    @Override
    public ChatConversationItem createOrGetSingle(Long userId, Long targetUserId) {
        requireLogin(userId);
        if (targetUserId == null || targetUserId.equals(userId)) throw new ApiException(400, "不能和自己聊天");
        User target = userMapper.selectById(targetUserId);
        if (target == null || Integer.valueOf(1).equals(target.getDisabled())) throw new ApiException(404, "成员不存在");

        List<ChatConversationMember> my = memberMapper.selectList(
                Wrappers.<ChatConversationMember>lambdaQuery().eq(ChatConversationMember::getUserId, userId));
        if (!my.isEmpty()) {
            List<Long> convIds = my.stream().map(ChatConversationMember::getConversationId).toList();
            List<ChatConversation> convs = conversationMapper.selectBatchIds(convIds);
            for (ChatConversation c : convs) {
                if (!"SINGLE".equals(c.getType())) continue;
                List<ChatConversationMember> ms = membersOf(c.getId());
                if (ms.size() == 2 && ms.stream().anyMatch(m -> m.getUserId().equals(targetUserId))) {
                    Map<Long, User> userMap = loadUsers(Set.of(userId, targetUserId));
                    return buildItem(userId, c, ms, userMap);
                }
            }
        }
        ChatConversation c = new ChatConversation();
        c.setType("SINGLE");
        c.setCreatorId(userId);
        conversationMapper.insert(c);
        insertMember(c.getId(), userId, "OWNER");
        insertMember(c.getId(), targetUserId, "MEMBER");
        Map<Long, User> userMap = loadUsers(Set.of(userId, targetUserId));
        return buildItem(userId, c, membersOf(c.getId()), userMap);
    }

    // ── 群聊 ─────────────────────────────────────────────────

    @Override
    @Transactional
    public ChatConversationItem createGroup(Long userId, String name, List<Long> memberIds) {
        requireLogin(userId);
        String n = name == null ? "" : name.trim();
        if (n.isBlank()) throw new ApiException(400, "请输入群名称");
        if (n.length() > 32) throw new ApiException(400, "群名称过长");
        Set<Long> ids = new HashSet<>();
        if (memberIds != null) {
            for (Long id : memberIds) {
                if (id != null && !id.equals(userId)) ids.add(id);
            }
        }
        if (ids.size() > 200) throw new ApiException(400, "单群最多 200 人");
        for (Long id : ids) {
            User u = userMapper.selectById(id);
            if (u == null || Integer.valueOf(1).equals(u.getDisabled())) throw new ApiException(400, "存在无效成员");
        }

        ChatConversation c = new ChatConversation();
        c.setType("GROUP");
        c.setName(n);
        c.setCreatorId(userId);
        conversationMapper.insert(c);
        insertMember(c.getId(), userId, "OWNER");
        for (Long id : ids) {
            insertMember(c.getId(), id, "MEMBER");
        }
        systemMessage(c.getId(), userId, "创建了群聊「" + n + "」");
        Map<Long, User> userMap = loadUsers(loadUserIds(Set.of(userId), ids));
        return buildItem(userId, c, membersOf(c.getId()), userMap);
    }

    // ── 消息 ─────────────────────────────────────────────────

    @Override
    public List<ChatMessageItem> listMessages(Long userId, Long conversationId, Long beforeId, int limit) {
        requireMember(conversationId, userId);
        int size = Math.min(Math.max(limit, 1), 100);
        LambdaQueryWrapper<ChatMessage> q = Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId);
        if (beforeId != null && beforeId > 0) q.lt(ChatMessage::getId, beforeId);
        q.orderByDesc(ChatMessage::getId).last("LIMIT " + size);
        List<ChatMessage> rows = messageMapper.selectList(q);
        Collections.reverse(rows);
        Set<Long> senderIds = rows.stream().map(ChatMessage::getSenderId).collect(Collectors.toSet());
        Set<Long> attachIds = rows.stream().map(ChatMessage::getAttachmentId)
                .filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, User> userMap = loadUsers(senderIds);
        Map<Long, ChatAttachment> attachMap = attachIds.isEmpty() ? Map.of()
                : attachmentMapper.selectBatchIds(attachIds).stream()
                .collect(Collectors.toMap(ChatAttachment::getId, Function.identity()));
        return rows.stream().map(m -> toMessageItem(m, userMap, attachMap)).toList();
    }

    @Override
    @Transactional
    public ChatMessageItem sendMessage(Long userId, Long conversationId, String content, String msgType, Long attachmentId, Long replyToId) {
        requireMember(conversationId, userId);
        String mt = msgType == null ? "TEXT" : msgType.trim().toUpperCase();
        if (!TEXT_TYPES.contains(mt)) mt = "TEXT";
        String text = content == null ? "" : content.trim();
        ChatAttachment attach = null;
        if (attachmentId != null) {
            attach = attachmentMapper.selectById(attachmentId);
            if (attach == null) throw new ApiException(404, "附件不存在");
            if (!attach.getUploaderId().equals(userId)) throw new ApiException(403, "只能发送自己上传的附件");
            if ("IMAGE".equals(mt) && !"IMAGE".equals(attach.getFileType())) throw new ApiException(400, "附件不是图片");
            if ("FILE".equals(mt) && "IMAGE".equals(attach.getFileType())) mt = "IMAGE";
        }
        if ("TEXT".equals(mt) && text.isEmpty()) throw new ApiException(400, "消息不能为空");
        if (text.length() > 4000) throw new ApiException(400, "消息过长");

        if (replyToId != null) {
            ChatMessage replyTarget = messageMapper.selectById(replyToId);
            if (replyTarget == null || !conversationId.equals(replyTarget.getConversationId())) {
                throw new ApiException(400, "引用消息不存在");
            }
        }

        ChatMessage m = new ChatMessage();
        m.setConversationId(conversationId);
        m.setSenderId(userId);
        m.setMsgType(mt);
        m.setContent(text.isEmpty() ? null : text);
        m.setAttachmentId(attachmentId);
        m.setReplyToId(replyToId);
        m.setRecalled(0);
        messageMapper.insert(m);

        ChatConversation c = conversationMapper.selectById(conversationId);
        if (c != null) {
            c.setLastMessageId(m.getId());
            c.setLastMessageAt(m.getCreateTime());
            conversationMapper.updateById(c);
        }
        ChatMessageItem item = toMessageItem(m, loadUsers(Set.of(userId)),
                attach == null ? Map.of() : Map.of(attach.getId(), attach));
        realtimeCollabService.broadcastChat(conversationId, memberIds(conversationId), userId,
                Map.of("conversationId", conversationId, "message", item));
        return item;
    }

    @Override
    @Transactional
    public ChatMessageItem recallMessage(Long userId, Long messageId) {
        requireLogin(userId);
        if (messageId == null) throw new ApiException(400, "参数错误");
        ChatMessage m = messageMapper.selectById(messageId);
        if (m == null) throw new ApiException(404, "消息不存在");
        if (!userId.equals(m.getSenderId())) throw new ApiException(403, "只能撤回自己的消息");
        if ("SYSTEM".equals(m.getMsgType())) throw new ApiException(400, "系统消息不可撤回");
        if (Integer.valueOf(1).equals(m.getRecalled())) throw new ApiException(400, "消息已撤回");
        if (m.getCreateTime() != null && m.getCreateTime().isBefore(LocalDateTime.now().minusMinutes(2))) {
            throw new ApiException(400, "超过 2 分钟的消息不可撤回");
        }
        m.setRecalled(1);
        messageMapper.updateById(m);
        ChatMessageItem item = toMessageItem(m, loadUsers(Set.of(userId)));
        realtimeCollabService.broadcastChatEvent(m.getConversationId(), memberIds(m.getConversationId()), userId,
                "CHAT_MESSAGE_RECALLED", Map.of("conversationId", m.getConversationId(), "messageId", m.getId()));
        return item;
    }

    @Override
    public List<ChatMessageItem> searchMessages(Long userId, Long conversationId, String keyword, int limit) {
        requireMember(conversationId, userId);
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) return List.of();
        if (kw.length() > 100) throw new ApiException(400, "关键词过长");
        int size = Math.min(Math.max(limit, 1), 100);
        LambdaQueryWrapper<ChatMessage> q = Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .ne(ChatMessage::getMsgType, "SYSTEM")
                .eq(ChatMessage::getRecalled, 0)
                .like(ChatMessage::getContent, kw)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + size);
        List<ChatMessage> rows = messageMapper.selectList(q);
        Set<Long> senderIds = rows.stream().map(ChatMessage::getSenderId).collect(Collectors.toSet());
        Set<Long> attachIds = rows.stream().map(ChatMessage::getAttachmentId)
                .filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, User> userMap = loadUsers(senderIds);
        Map<Long, ChatAttachment> attachMap = attachIds.isEmpty() ? Map.of()
                : attachmentMapper.selectBatchIds(attachIds).stream()
                .collect(Collectors.toMap(ChatAttachment::getId, Function.identity()));
        return rows.stream().map(m -> toMessageItem(m, userMap, attachMap)).toList();
    }

    @Override
    public List<Long> readUserIds(Long userId, Long conversationId, Long messageId) {
        requireMember(conversationId, userId);
        if (messageId == null) throw new ApiException(400, "参数错误");
        ChatMessage msg = messageMapper.selectById(messageId);
        if (msg == null || !conversationId.equals(msg.getConversationId())) throw new ApiException(404, "消息不存在");
        return membersOf(conversationId).stream()
                .filter(m -> m.getLastReadMessageId() != null && m.getLastReadMessageId() >= messageId)
                .map(ChatConversationMember::getUserId)
                .toList();
    }

    @Override
    @Transactional
    public ChatMessageItem forwardMessage(Long userId, Long messageId, Long targetConversationId) {
        requireLogin(userId);
        if (messageId == null || targetConversationId == null) throw new ApiException(400, "参数错误");
        ChatMessage src = messageMapper.selectById(messageId);
        if (src == null || "SYSTEM".equals(src.getMsgType())) throw new ApiException(404, "消息不存在");
        if (Integer.valueOf(1).equals(src.getRecalled())) throw new ApiException(400, "消息已撤回不可转发");
        requireMember(src.getConversationId(), userId);
        requireMember(targetConversationId, userId);

        ChatMessage m = new ChatMessage();
        m.setConversationId(targetConversationId);
        m.setSenderId(userId);
        m.setMsgType(src.getMsgType());
        m.setContent(src.getContent());
        m.setAttachmentId(src.getAttachmentId());
        m.setRecalled(0);
        if ("TEXT".equals(src.getMsgType()) && notBlank(src.getContent())) {
            m.setContent(src.getContent().length() > 4000 ? src.getContent().substring(0, 4000) : src.getContent());
        }
        messageMapper.insert(m);

        ChatConversation c = conversationMapper.selectById(targetConversationId);
        if (c != null) {
            c.setLastMessageId(m.getId());
            c.setLastMessageAt(m.getCreateTime());
            conversationMapper.updateById(c);
        }
        ChatAttachment attach = m.getAttachmentId() == null ? null : attachmentMapper.selectById(m.getAttachmentId());
        Map<Long, ChatAttachment> attachMap = attach == null ? Map.of() : Map.of(attach.getId(), attach);
        ChatMessageItem item = toMessageItem(m, loadUsers(Set.of(userId)), attachMap);
        realtimeCollabService.broadcastChat(targetConversationId, memberIds(targetConversationId), userId,
                Map.of("conversationId", targetConversationId, "message", item));
        return item;
    }

    @Override
    public ChatMessageItem markRead(Long userId, Long conversationId) {
        ChatConversationMember mine = requireMember(conversationId, userId);
        ChatConversation c = conversationMapper.selectById(conversationId);
        Long lastId = c == null ? null : c.getLastMessageId();
        if (lastId == null) {
            ChatMessage last = messageMapper.selectOne(
                    Wrappers.<ChatMessage>lambdaQuery()
                            .eq(ChatMessage::getConversationId, conversationId)
                            .orderByDesc(ChatMessage::getId)
                            .last("LIMIT 1"));
            lastId = last == null ? null : last.getId();
        }
        if (lastId != null) {
            mine.setLastReadMessageId(lastId);
            memberMapper.updateById(mine);
            // 广播已读事件给会话成员（前端用于单聊已读回执，注意事件会包含自己，前端按 userId 过滤）
            realtimeCollabService.broadcastChatEvent(conversationId, memberIds(conversationId), userId,
                    "CHAT_MESSAGE_READ", Map.of("conversationId", conversationId, "userId", userId, "lastReadMessageId", lastId));
        }
        if (lastId == null) return null;
        ChatMessage last = messageMapper.selectById(lastId);
        if (last == null) return null;
        return toMessageItem(last, loadUsers(Set.of(last.getSenderId())));
    }

    // ── 群成员管理 ───────────────────────────────────────────

    @Override
    @Transactional
    public void addMembers(Long userId, Long conversationId, List<Long> userIds) {
        requireOwner(conversationId, userId);
        if (userIds == null || userIds.isEmpty()) return;
        int added = 0;
        List<String> names = new ArrayList<>();
        for (Long id : new HashSet<>(userIds)) {
            if (id == null) continue;
            if (isMember(conversationId, id)) continue;
            User u = userMapper.selectById(id);
            if (u == null || Integer.valueOf(1).equals(u.getDisabled())) continue;
            insertMember(conversationId, id, "MEMBER");
            added++;
            names.add(displayName(u));
        }
        if (added > 0) {
            systemMessage(conversationId, userId, "邀请了 " + added + " 位成员加入群聊");
        }
    }

    @Override
    @Transactional
    public void removeMember(Long userId, Long conversationId, Long targetUserId) {
        ChatConversationMember mine = requireMember(conversationId, userId);
        if (targetUserId == null) throw new ApiException(400, "参数错误");
        ChatConversationMember target = memberMapper.selectOne(
                Wrappers.<ChatConversationMember>lambdaQuery()
                        .eq(ChatConversationMember::getConversationId, conversationId)
                        .eq(ChatConversationMember::getUserId, targetUserId));
        if (target == null) throw new ApiException(404, "成员不存在");
        boolean self = targetUserId.equals(userId);
        if (!self && !"OWNER".equals(mine.getRole())) throw new ApiException(403, "仅群主可移除成员");
        if ("OWNER".equals(target.getRole()) && !self) throw new ApiException(400, "不能移除群主");
        memberMapper.deleteById(target.getId());
        User targetUser = userMapper.selectById(targetUserId);
        if (self) {
            systemMessage(conversationId, userId, "退出了群聊");
        } else {
            systemMessage(conversationId, userId, "将 " + (targetUser == null ? "成员" : displayName(targetUser)) + " 移出了群聊");
        }
    }

    @Override
    @Transactional
    public void rename(Long userId, Long conversationId, String name) {
        requireOwner(conversationId, userId);
        String n = name == null ? "" : name.trim();
        if (n.isBlank()) throw new ApiException(400, "请输入群名称");
        if (n.length() > 32) throw new ApiException(400, "群名称过长");
        ChatConversation c = conversationMapper.selectById(conversationId);
        if (c == null) throw new ApiException(404, "会话不存在");
        c.setName(n);
        conversationMapper.updateById(c);
        systemMessage(conversationId, userId, "将群名称修改为「" + n + "」");
    }

    @Override
    @Transactional
    public void dissolve(Long userId, Long conversationId) {
        requireOwner(conversationId, userId);
        memberMapper.delete(Wrappers.<ChatConversationMember>lambdaQuery()
                .eq(ChatConversationMember::getConversationId, conversationId));
        messageMapper.delete(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId));
        conversationMapper.deleteById(conversationId);
    }

    // ── 成员列表 / 附件 ───────────────────────────────────────

    @Override
    public List<ChatMemberItem> listAvailableMembers(Long userId) {
        requireLogin(userId);
        List<User> users = userMapper.selectList(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getDisabled, 0)
                        .ne(User::getId, userId)
                        .orderByAsc(User::getId));
        return users.stream().map(u -> new ChatMemberItem(
                u.getId(), u.getUsername(), u.getNickname(), u.getAvatarUrl(), u.getEmail(), u.getStatus())).toList();
    }

    @Override
    public List<ChatMemberItem> listConversationMembers(Long userId, Long conversationId) {
        requireMember(conversationId, userId);
        List<ChatConversationMember> members = membersOf(conversationId);
        if (members.isEmpty()) return List.of();
        Set<Long> ids = members.stream().map(ChatConversationMember::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = loadUsers(ids);
        return members.stream()
                .map(m -> {
                    User u = userMap.get(m.getUserId());
                    return new ChatMemberItem(
                            m.getUserId(),
                            u == null ? "已注销成员" : u.getUsername(),
                            u == null ? null : u.getNickname(),
                            u == null ? null : u.getAvatarUrl(),
                            u == null ? null : u.getEmail(),
                            u == null ? null : u.getStatus());
                })
                .toList();
    }

    @Override
    public ChatAttachmentItem uploadAttachment(Long userId, MultipartFile file) {
        requireLogin(userId);
        if (file == null || file.isEmpty()) throw new ApiException(400, "请选择文件");
        long size = file.getSize();
        long max = Math.max(1, (attachmentProperties.getMaxSizeMb() == null ? 50 : attachmentProperties.getMaxSizeMb())) * 1024L * 1024L;
        if (size <= 0) throw new ApiException(400, "文件为空");
        if (size > max) throw new ApiException(413, "文件过大，最大支持 " + (max / 1024 / 1024) + "MB");

        String originalName = normalizeName(file.getOriginalFilename());
        String ext = safeExt(originalName);
        if (isDangerousExt(ext)) throw new ApiException(400, "不支持该文件类型");

        String baseDir = (attachmentProperties.getBaseDir() == null || attachmentProperties.getBaseDir().isBlank())
                ? "data/chat-attachments"
                : attachmentProperties.getBaseDir().trim() + "/chat";
        String ym = LocalDate.now().toString().replace("-", "").substring(0, 6);
        String key = UUID.randomUUID().toString().replace("-", "");
        String filename = key + (ext.isEmpty() ? "" : ("." + ext));
        Path dir = Paths.get(baseDir, ym).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new ApiException(500, "附件目录不可用");
        }
        Path path = dir.resolve(filename).toAbsolutePath().normalize();
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new ApiException(500, "保存失败");
        }

        String ct = file.getContentType();
        ChatAttachment a = new ChatAttachment();
        a.setUploaderId(userId);
        a.setOriginalName(originalName);
        a.setContentType(ct);
        a.setFileType(ct != null && ct.toLowerCase().startsWith("image/") ? "IMAGE" : "FILE");
        a.setSizeBytes(size);
        a.setStorageKey(key);
        a.setStoragePath(path.toString());
        attachmentMapper.insert(a);
        return toAttachmentItem(a);
    }

    @Override
    public DownloadFile loadAttachment(Long userId, Long attachmentId, boolean preview, boolean inlineImage) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (attachmentId == null) throw new ApiException(400, "id不能为空");
        ChatAttachment a = attachmentMapper.selectById(attachmentId);
        if (a == null) throw new ApiException(404, "附件不存在");
        ChatMessage msg = messageMapper.selectOne(
                Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getAttachmentId, attachmentId)
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT 1"));
        if (msg == null) throw new ApiException(404, "附件未关联消息");
        requireMember(msg.getConversationId(), userId);
        Path p = safePath(a.getStoragePath());
        if (p == null || !Files.exists(p)) throw new ApiException(404, "文件不存在");
        String ct = resolveContentType(a, p, preview, inlineImage);
        return new DownloadFile(a.getOriginalName(), ct, p);
    }

    @Override
    public List<Long> memberIds(Long conversationId) {
        return membersOf(conversationId).stream().map(ChatConversationMember::getUserId).toList();
    }

    @Override
    public boolean isMember(Long conversationId, Long userId) {
        if (conversationId == null || userId == null) return false;
        Long count = memberMapper.selectCount(Wrappers.<ChatConversationMember>lambdaQuery()
                .eq(ChatConversationMember::getConversationId, conversationId)
                .eq(ChatConversationMember::getUserId, userId));
        return count != null && count > 0;
    }

    // ── 会话详情 / 设置 ──────────────────────────────────────

    @Override
    public ChatConversationDetail conversationDetail(Long userId, Long conversationId) {
        requireMember(conversationId, userId);
        ChatConversation c = conversationMapper.selectById(conversationId);
        if (c == null) throw new ApiException(404, "会话不存在");
        List<ChatConversationMember> members = membersOf(conversationId);
        ChatConversationMember mine = members.stream()
                .filter(m -> m.getUserId().equals(userId)).findFirst().orElse(null);
        return new ChatConversationDetail(c.getId(), c.getType(),
                notBlank(c.getName()) ? c.getName() : "群聊",
                c.getAnnouncement(),
                c.getCreatorId(), (long) members.size(),
                mine == null ? "MEMBER" : mine.getRole(),
                mine != null && Integer.valueOf(1).equals(mine.getMuted()));
    }

    @Override
    public void updateAnnouncement(Long userId, Long conversationId, String announcement) {
        requireMember(conversationId, userId);
        ChatConversation c = conversationMapper.selectById(conversationId);
        if (c == null) throw new ApiException(404, "会话不存在");
        if (!"GROUP".equals(c.getType())) throw new ApiException(400, "仅群聊可设置公告");
        String v = announcement == null ? "" : announcement.trim();
        if (v.length() > 500) throw new ApiException(400, "公告内容过长");
        c.setAnnouncement(v);
        conversationMapper.updateById(c);
    }

    @Override
    public List<ChatFileItem> listSharedFiles(Long userId, Long conversationId, String fileType) {
        requireMember(conversationId, userId);
        boolean filter = fileType != null && !fileType.isBlank() && !"ALL".equalsIgnoreCase(fileType);
        List<ChatMessage> msgs = messageMapper.selectList(
                Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getConversationId, conversationId)
                        .in(ChatMessage::getMsgType, "IMAGE", "FILE")
                        .isNotNull(ChatMessage::getAttachmentId)
                        .eq(filter, ChatMessage::getMsgType, fileType)
                        .orderByDesc(ChatMessage::getId));
        if (msgs.isEmpty()) return List.of();
        List<Long> attachIds = msgs.stream().map(ChatMessage::getAttachmentId).toList();
        List<ChatAttachment> attaches = attachmentMapper.selectBatchIds(attachIds);
        Map<Long, ChatAttachment> attachMap = attaches.stream()
                .collect(Collectors.toMap(ChatAttachment::getId, Function.identity()));
        Set<Long> senderIds = msgs.stream().map(ChatMessage::getSenderId).collect(Collectors.toSet());
        Map<Long, User> userMap = loadUsers(senderIds);
        List<ChatFileItem> items = new ArrayList<>();
        for (ChatMessage m : msgs) {
            ChatAttachment a = m.getAttachmentId() == null ? null : attachMap.get(m.getAttachmentId());
            if (a == null) continue;
            User sender = m.getSenderId() == null ? null : userMap.get(m.getSenderId());
            items.add(new ChatFileItem(a.getId(), a.getOriginalName(), a.getContentType(), a.getFileType(),
                    a.getSizeBytes(), m.getSenderId(), displayName(sender), m.getCreateTime()));
        }
        return items;
    }

    @Override
    public void updateMuted(Long userId, Long conversationId, boolean muted) {
        ChatConversationMember mine = requireMember(conversationId, userId);
        mine.setMuted(muted ? 1 : 0);
        memberMapper.updateById(mine);
    }

    @Override
    public void updatePinned(Long userId, Long conversationId, boolean pinned) {
        ChatConversationMember mine = requireMember(conversationId, userId);
        mine.setPinned(pinned ? 1 : 0);
        memberMapper.updateById(mine);
    }

    @Override
    public void markUnread(Long userId, Long conversationId) {
        ChatConversationMember mine = requireMember(conversationId, userId);
        ChatConversation c = conversationMapper.selectById(conversationId);
        Long lastId = c == null ? null : c.getLastMessageId();
        if (lastId == null) {
            ChatMessage last = messageMapper.selectOne(Wrappers.<ChatMessage>lambdaQuery()
                    .eq(ChatMessage::getConversationId, conversationId)
                    .orderByDesc(ChatMessage::getId)
                    .last("LIMIT 1"));
            lastId = last == null ? null : last.getId();
        }
        if (lastId == null) return;
        // 标为未读：已读位点回退到最新消息之前（至少产生 1 条未读）
        long target = lastId - 1;
        if (target < 0) target = 0;
        mine.setLastReadMessageId(target);
        memberMapper.updateById(mine);
    }

    // ── 内部工具 ─────────────────────────────────────────────

    /** 单聊：对方已读到的最大消息 id（未读为 null） */
    private Long peerReadMessageId(Long conversationId, Long me, List<ChatConversationMember> members) {
        for (ChatConversationMember m : members) {
            if (m.getConversationId().equals(conversationId) && me != null && !me.equals(m.getUserId())) {
                return m.getLastReadMessageId();
            }
        }
        return null;
    }

    private ChatConversationItem buildItem(Long userId, ChatConversation c,
                                           List<ChatConversationMember> members, Map<Long, User> userMap) {
        ChatConversationMember mine = members.stream()
                .filter(m -> m.getUserId().equals(userId)).findFirst().orElse(null);
        ChatMessage last = c.getLastMessageId() == null ? null : messageMapper.selectById(c.getLastMessageId());
        if (last == null) {
            last = messageMapper.selectOne(Wrappers.<ChatMessage>lambdaQuery()
                    .eq(ChatMessage::getConversationId, c.getId())
                    .orderByDesc(ChatMessage::getId)
                    .last("LIMIT 1"));
        }
        long lastRead = mine == null || mine.getLastReadMessageId() == null ? 0L : mine.getLastReadMessageId();
        long unread = last == null || last.getId() <= lastRead ? 0L : countUnread(c.getId(), lastRead);
        ChatSenderItem other = otherUser(userId, c, members, userMap);
        String display = "SINGLE".equals(c.getType())
                ? (other != null && notBlank(other.getNickname()) ? other.getNickname() : other != null ? other.getUsername() : "会话")
                : notBlank(c.getName()) ? c.getName() : "群聊";
        ChatMessageItem lastItem = last == null ? null : toMessageItem(last, userMap);
        ChatConversationItem item = new ChatConversationItem(c.getId(), c.getType(), display, c.getCreatorId(),
                (long) members.size(), mine == null ? "MEMBER" : mine.getRole(),
                unread, lastItem, other, c.getCreateTime());
        item.setPinned(mine != null && Integer.valueOf(1).equals(mine.getPinned()));
        item.setMuted(mine != null && Integer.valueOf(1).equals(mine.getMuted()));
        if ("SINGLE".equals(c.getType())) {
            item.setPeerReadMessageId(peerReadMessageId(c.getId(), userId, members));
        }
        return item;
    }

    private ChatSenderItem otherUser(Long me, ChatConversation c, List<ChatConversationMember> members, Map<Long, User> userMap) {
        if (!"SINGLE".equals(c.getType())) return null;
        for (ChatConversationMember m : members) {
            if (me != null && me.equals(m.getUserId())) continue;
            User u = userMap.get(m.getUserId());
            if (u != null) return toSender(u);
        }
        return null;
    }

    private ChatMessageItem toMessageItem(ChatMessage m, Map<Long, User> userMap) {
        return toMessageItem(m, userMap, Map.of());
    }

    private ChatMessageItem toMessageItem(ChatMessage m, Map<Long, User> userMap, Map<Long, ChatAttachment> attachMap) {
        User sender = m.getSenderId() == null ? null : userMap.get(m.getSenderId());
        if (sender == null) {
            User ghost = new User();
            ghost.setId(m.getSenderId());
            ghost.setUsername("已注销成员");
            sender = ghost;
        }
        ChatAttachmentItem att = null;
        if (m.getAttachmentId() != null) {
            ChatAttachment a = attachMap.get(m.getAttachmentId());
            if (a != null) att = toAttachmentItem(a);
        }
        boolean recalled = Integer.valueOf(1).equals(m.getRecalled());
        ChatMessageItem replyTo = null;
        if (m.getReplyToId() != null && !recalled) {
            replyTo = replySummary(m.getReplyToId(), userMap);
        }
        return new ChatMessageItem(m.getId(), m.getConversationId(), m.getSenderId(),
                toSender(sender), m.getMsgType(),
                recalled ? null : m.getContent(),
                recalled ? null : att,
                replyTo, recalled, m.getCreateTime());
    }

    /** 引用摘要：仅取目标消息的关键信息，不嵌套引用 */
    private ChatMessageItem replySummary(Long replyToId, Map<Long, User> userMap) {
        ChatMessage target = messageMapper.selectById(replyToId);
        if (target == null) return null;
        User ts = target.getSenderId() == null ? null : userMap.get(target.getSenderId());
        if (ts == null) {
            User ghost = new User();
            ghost.setId(target.getSenderId());
            ghost.setUsername("已注销成员");
            ts = ghost;
        }
        boolean targetRecalled = Integer.valueOf(1).equals(target.getRecalled());
        String summary = targetRecalled ? "消息已撤回"
                : "IMAGE".equals(target.getMsgType()) ? "[图片]"
                : "FILE".equals(target.getMsgType()) ? "[文件] " + (target.getContent() == null ? "" : target.getContent())
                : target.getContent() == null ? "" : target.getContent();
        if (summary.length() > 80) summary = summary.substring(0, 80) + "…";
        return new ChatMessageItem(target.getId(), target.getConversationId(), target.getSenderId(),
                toSender(ts), targetRecalled ? "TEXT" : target.getMsgType(), summary, null, null, targetRecalled, target.getCreateTime());
    }

    private ChatSenderItem toSender(User u) {
        return new ChatSenderItem(u.getId(), u.getUsername(), u.getNickname(), u.getAvatarUrl(), u.getStatus());
    }

    private ChatAttachmentItem toAttachmentItem(ChatAttachment a) {
        return new ChatAttachmentItem(a.getId(), a.getOriginalName(), a.getContentType(),
                a.getFileType(), a.getSizeBytes(), a.getCreateTime());
    }

    private ChatConversationMember requireMember(Long conversationId, Long userId) {
        requireLogin(userId);
        ChatConversationMember m = memberMapper.selectOne(
                Wrappers.<ChatConversationMember>lambdaQuery()
                        .eq(ChatConversationMember::getConversationId, conversationId)
                        .eq(ChatConversationMember::getUserId, userId));
        if (m == null) throw new ApiException(403, "你不在该会话中");
        return m;
    }

    private void requireOwner(Long conversationId, Long userId) {
        ChatConversationMember m = requireMember(conversationId, userId);
        if (!"OWNER".equals(m.getRole())) throw new ApiException(403, "仅群主可执行该操作");
    }

    private void insertMember(Long conversationId, Long userId, String role) {
        ChatConversationMember m = new ChatConversationMember();
        m.setConversationId(conversationId);
        m.setUserId(userId);
        m.setRole(role);
        m.setMuted(0);
        memberMapper.insert(m);
    }

    private List<ChatConversationMember> membersOf(Long conversationId) {
        return memberMapper.selectList(Wrappers.<ChatConversationMember>lambdaQuery()
                .eq(ChatConversationMember::getConversationId, conversationId));
    }

    private long countUnread(Long conversationId, long lastReadId) {
        Long c = messageMapper.selectCount(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .gt(ChatMessage::getId, lastReadId));
        return c == null ? 0L : c;
    }

    private void systemMessage(Long conversationId, Long actorUserId, String text) {
        ChatMessage m = new ChatMessage();
        m.setConversationId(conversationId);
        m.setSenderId(actorUserId);
        m.setMsgType("SYSTEM");
        m.setContent(text);
        messageMapper.insert(m);
        ChatConversation c = conversationMapper.selectById(conversationId);
        if (c != null) {
            c.setLastMessageId(m.getId());
            c.setLastMessageAt(m.getCreateTime());
            conversationMapper.updateById(c);
        }
        Map<Long, User> userMap = loadUsers(Set.of(actorUserId));
        ChatMessageItem item = toMessageItem(m, userMap);
        realtimeCollabService.broadcastChat(conversationId, memberIds(conversationId), actorUserId,
                Map.of("conversationId", conversationId, "message", item));
    }

    private Map<Long, User> loadUsers(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        List<User> users = userMapper.selectBatchIds(new ArrayList<>(userIds));
        return users.stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Set<Long> loadUserIds(Set<Long> base, Set<Long> extra) {
        Set<Long> ids = new HashSet<>(base);
        ids.addAll(extra);
        return ids;
    }

    private void requireLogin(Long userId) {
        if (userId == null) throw new ApiException(401, "未登录");
    }

    private static String displayName(User u) {
        if (u == null) return "成员";
        return notBlank(u.getNickname()) ? u.getNickname() : u.getUsername();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    // ── 文件工具（与附件模块同构） ────────────────────────────

    private static String normalizeName(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isBlank()) return "file";
        v = v.replace("\\", "/");
        int idx = v.lastIndexOf('/');
        if (idx >= 0) v = v.substring(idx + 1);
        if (v.isBlank()) return "file";
        if (v.length() > 255) v = v.substring(0, 255);
        return v;
    }

    private static String safeExt(String filename) {
        if (filename == null) return "";
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) return "";
        String ext = filename.substring(idx + 1).trim().toLowerCase();
        if (ext.length() > 12) return "";
        for (int i = 0; i < ext.length(); i++) {
            char c = ext.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9')) return "";
        }
        return ext;
    }

    private static boolean isDangerousExt(String ext) {
        if (ext == null || ext.isBlank()) return false;
        Set<String> deny = Set.of("exe", "sh", "bat", "cmd", "ps1", "jar", "war", "class", "msi");
        return deny.contains(ext);
    }

    private static String resolveContentType(ChatAttachment a, Path path, boolean preview, boolean inlineImage) {
        String ct = a.getContentType();
        if (ct != null && !ct.isBlank()) {
            if (inlineImage && ct.toLowerCase().startsWith("image/")) return ct;
            if (!preview) return ct;
            if (isPreviewable(ct)) return ct;
        }
        try {
            String guessed = Files.probeContentType(path);
            if (guessed != null && !guessed.isBlank()) {
                if (inlineImage && guessed.toLowerCase().startsWith("image/")) return guessed;
                if (!preview) return guessed;
                if (isPreviewable(guessed)) return guessed;
            }
        } catch (Exception ignored) {
        }
        String ext = safeExt(a.getOriginalName());
        String fallback = switch (ext) {
            case "png", "jpg", "jpeg", "gif", "webp", "svg" -> "image/" + ext;
            case "pdf" -> "application/pdf";
            case "txt", "log", "md", "json", "xml", "yml", "yaml", "csv" -> "text/plain; charset=UTF-8";
            default -> "application/octet-stream";
        };
        if (inlineImage && fallback.startsWith("image/")) return fallback;
        if (preview && !isPreviewable(fallback)) return "application/octet-stream";
        return fallback;
    }

    private static boolean isPreviewable(String ct) {
        String v = ct == null ? "" : ct.toLowerCase();
        return v.startsWith("image/") || v.startsWith("text/") || v.contains("json")
                || v.contains("xml") || v.equals("application/pdf");
    }

    private static Path safePath(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Paths.get(raw).toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
    }
}
