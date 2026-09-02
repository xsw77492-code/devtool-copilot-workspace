package com.devtoolcopilot.chat.service;

import com.devtoolcopilot.chat.dto.ChatAttachmentItem;
import com.devtoolcopilot.chat.dto.ChatConversationDetail;
import com.devtoolcopilot.chat.dto.ChatConversationItem;
import com.devtoolcopilot.chat.dto.ChatFileItem;
import com.devtoolcopilot.chat.dto.ChatMemberItem;
import com.devtoolcopilot.chat.dto.ChatMessageItem;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

public interface ChatService {
    /** 会话列表（含未读数、最后消息、对方资料），按最后消息时间倒序 */
    List<ChatConversationItem> listConversations(Long userId);

    /** 创建或获取单聊会话 */
    ChatConversationItem createOrGetSingle(Long userId, Long targetUserId);

    /** 创建群聊 */
    ChatConversationItem createGroup(Long userId, String name, List<Long> memberIds);

    /** 历史消息分页（游标 beforeId 前翻） */
    List<ChatMessageItem> listMessages(Long userId, Long conversationId, Long beforeId, int limit);

    /** 发送消息（TEXT/IMAGE/FILE），支持引用回复 replyToId，落库后广播给会话成员 */
    ChatMessageItem sendMessage(Long userId, Long conversationId, String content, String msgType, Long attachmentId, Long replyToId);

    /** 撤回消息（仅本人、2 分钟内、非系统消息），全员可见占位 */
    ChatMessageItem recallMessage(Long userId, Long messageId);

    /** 会话内消息搜索（排除系统消息与已撤回） */
    List<ChatMessageItem> searchMessages(Long userId, Long conversationId, String keyword, int limit);

    /** 已读该消息的成员 userId 列表（基于 last_read_message_id 位点） */
    List<Long> readUserIds(Long userId, Long conversationId, Long messageId);

    /** 转发消息到其他会话（TEXT/IMAGE/FILE 复制，附件引用同一文件） */
    ChatMessageItem forwardMessage(Long userId, Long messageId, Long targetConversationId);

    /** 标记已读，返回会话最新一条消息（用于同步） */
    ChatMessageItem markRead(Long userId, Long conversationId);

    /** 添加成员（仅 OWNER） */
    void addMembers(Long userId, Long conversationId, List<Long> userIds);

    /** 移除成员（OWNER 移除他人，或成员自己退群） */
    void removeMember(Long userId, Long conversationId, Long targetUserId);

    /** 群聊改名（仅 OWNER） */
    void rename(Long userId, Long conversationId, String name);

    /** 解散群聊（仅 OWNER） */
    void dissolve(Long userId, Long conversationId);

    /** 可聊天成员列表（全部启用用户，排除自己） */
    List<ChatMemberItem> listAvailableMembers(Long userId);

    /** 会话成员列表（校验调用者为会话成员），单聊/群聊通用 */
    List<ChatMemberItem> listConversationMembers(Long userId, Long conversationId);

    /** 上传附件（图片/文件），返回附件信息 */
    ChatAttachmentItem uploadAttachment(Long userId, MultipartFile file);

    /** 加载附件文件（下载/预览/内联图片），校验调用者为会话成员 */
    DownloadFile loadAttachment(Long userId, Long attachmentId, boolean preview, boolean inlineImage);

    /** 会话详情（含群公告、成员数、免打扰状态），校验调用者为会话成员 */
    ChatConversationDetail conversationDetail(Long userId, Long conversationId);

    /** 更新群公告（仅群聊且调用者为群成员） */
    void updateAnnouncement(Long userId, Long conversationId, String announcement);

    /** 会话共享文件列表（图片/文件，按时间倒序），fileType 为空则全部 */
    List<ChatFileItem> listSharedFiles(Long userId, Long conversationId, String fileType);

    /** 更新当前成员免打扰状态 */
    void updateMuted(Long userId, Long conversationId, boolean muted);

    /** 置顶 / 取消置顶会话 */
    void updatePinned(Long userId, Long conversationId, boolean pinned);

    /** 手动标为未读（已读位点回退到最新消息之前） */
    void markUnread(Long userId, Long conversationId);

    /** 会话全部成员 userId */
    List<Long> memberIds(Long conversationId);

    /** 是否会话成员 */
    boolean isMember(Long conversationId, Long userId);

    record DownloadFile(String filename, String contentType, Path path) {
    }
}
