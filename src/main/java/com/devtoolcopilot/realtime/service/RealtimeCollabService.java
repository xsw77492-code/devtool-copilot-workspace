package com.devtoolcopilot.realtime.service;

import java.util.List;

public interface RealtimeCollabService {
    void broadcast(Long projectId, Long actorUserId, String type, Object payload);

    /**
     * 向团队协作频道广播事件（群组/邀请/成员/动态），
     * 团队事件由 team_group_activity 落库，此处仅推送在线订阅者，不重复落库。
     */
    void broadcastTeam(Long actorUserId, String type, Object payload);

    /**
     * 向指定会话成员精确推送聊天消息（type=CHAT_MESSAGE）。
     * 消息已由调用方落库，此处仅推送在线订阅者。
     *
     * @param conversationId 会话 id
     * @param memberIds      会话成员 userId 列表（投递范围）
     * @param actorUserId    发送者 userId
     * @param payload        载荷对象（通常为 {conversationId, message}）
     */
    void broadcastChat(Long conversationId, List<Long> memberIds, Long actorUserId, Object payload);

    /**
     * 向指定会话成员推送自定义聊天事件（如 CHAT_MESSAGE_RECALLED）。
     * 不落库，仅推送给在线订阅者。
     *
     * @param type 事件类型，如 CHAT_MESSAGE_RECALLED
     */
    void broadcastChatEvent(Long conversationId, List<Long> memberIds, Long actorUserId, String type, Object payload);
}

