package com.devtoolcopilot.realtime.websocket;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.devtoolcopilot.project.entity.ProjectMemberRole;
import com.devtoolcopilot.project.mapper.ProjectMemberMapper;
import com.devtoolcopilot.realtime.entity.ProjectPresenceSession;
import com.devtoolcopilot.realtime.mapper.ProjectPresenceSessionMapper;
import com.devtoolcopilot.user.entity.User;
import com.devtoolcopilot.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class RealtimeSessionHub {
    private static final long OFFLINE_GRACE_SECONDS = 90;
    private final Map<Long, CopyOnWriteArraySet<WebSocketSession>> projectSessions = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, Long> sessionUsers = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, Long> sessionProjects = new ConcurrentHashMap<>();
    private final Map<String, PresenceState> presenceStates = new ConcurrentHashMap<>();
    private final ProjectMemberMapper projectMemberMapper;
    private final UserMapper userMapper;
    private final ProjectPresenceSessionMapper presenceSessionMapper;
    private final ObjectMapper objectMapper;

    public RealtimeSessionHub(ProjectMemberMapper projectMemberMapper,
                              UserMapper userMapper,
                              ProjectPresenceSessionMapper presenceSessionMapper,
                              ObjectMapper objectMapper) {
        this.projectMemberMapper = projectMemberMapper;
        this.userMapper = userMapper;
        this.presenceSessionMapper = presenceSessionMapper;
        this.objectMapper = objectMapper;
    }

    public void subscribe(Long projectId, Long userId, WebSocketSession session) {
        if (projectId == null || userId == null || session == null) return;
        projectSessions.computeIfAbsent(projectId, k -> new CopyOnWriteArraySet<>()).add(session);
        sessionUsers.put(session, userId);
        sessionProjects.put(session, projectId);
        onPresenceJoin(projectId, userId, session);
        broadcastPresence(projectId);
    }

    public void unsubscribe(Long projectId, WebSocketSession session) {
        if (projectId == null || session == null) return;
        Set<WebSocketSession> set = projectSessions.get(projectId);
        if (set == null) return;
        set.remove(session);
        if (set.isEmpty()) projectSessions.remove(projectId);
        sessionUsers.remove(session);
        sessionProjects.remove(session);
        onPresenceLeave(session);
        broadcastPresence(projectId);
    }

    public void removeSession(WebSocketSession session) {
        if (session == null) return;
        Long pid = sessionProjects.get(session);
        if (pid != null) {
            Set<WebSocketSession> set = projectSessions.get(pid);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) projectSessions.remove(pid);
            }
        } else {
            for (Map.Entry<Long, CopyOnWriteArraySet<WebSocketSession>> en : projectSessions.entrySet()) {
                en.getValue().remove(session);
                if (en.getValue().isEmpty()) projectSessions.remove(en.getKey());
            }
        }
        sessionUsers.remove(session);
        sessionProjects.remove(session);
        onPresenceLeave(session);
        if (pid != null) broadcastPresence(pid);
    }

    public void broadcast(Long projectId, String text) {
        if (projectId == null || text == null) return;
        Set<WebSocketSession> set = projectSessions.get(projectId);
        if (set == null || set.isEmpty()) return;
        TextMessage msg = new TextMessage(text);
        for (WebSocketSession s : set) {
            try {
                Long userId = sessionUsers.get(s);
                if (userId == null) {
                    set.remove(s);
                    continue;
                }
                ProjectMemberRole r = projectMemberMapper.findRole(projectId, userId);
                if (r == null) {
                    set.remove(s);
                    sessionUsers.remove(s);
                    continue;
                }
                if (s.isOpen()) {
                    s.sendMessage(msg);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public void updatePresenceView(WebSocketSession session, String viewType, Long viewId) {
        if (session == null) return;
        Long projectId = sessionProjects.get(session);
        Long userId = sessionUsers.get(session);
        if (projectId == null || userId == null) return;
        PresenceState st = presenceStates.get(session.getId());
        if (st == null) return;
        st.viewType = viewType;
        st.viewId = viewId;
        st.lastSeenTime = LocalDateTime.now();
        st.online = true;
        st.disconnectTime = null;

        UpdateWrapper<ProjectPresenceSession> uw = new UpdateWrapper<>();
        uw.eq("ws_session_id", session.getId());
        uw.set("view_type", viewType);
        uw.set("view_id", viewId);
        uw.set("last_seen_time", st.lastSeenTime);
        uw.set("disconnect_time", null);
        presenceSessionMapper.update(null, uw);

        broadcastPresence(projectId);
    }

    public void updatePresenceEditing(WebSocketSession session, Boolean editing) {
        if (session == null) return;
        Long projectId = sessionProjects.get(session);
        Long userId = sessionUsers.get(session);
        if (projectId == null || userId == null) return;
        PresenceState st = presenceStates.get(session.getId());
        if (st == null) return;
        st.isEditing = Boolean.TRUE.equals(editing);
        st.lastSeenTime = LocalDateTime.now();
        st.online = true;
        st.disconnectTime = null;

        UpdateWrapper<ProjectPresenceSession> uw = new UpdateWrapper<>();
        uw.eq("ws_session_id", session.getId());
        uw.set("is_editing", st.isEditing ? 1 : 0);
        uw.set("last_seen_time", st.lastSeenTime);
        uw.set("disconnect_time", null);
        presenceSessionMapper.update(null, uw);

        broadcastPresence(projectId);
    }

    public void updatePresenceHeartbeat(WebSocketSession session) {
        if (session == null) return;
        Long projectId = sessionProjects.get(session);
        Long userId = sessionUsers.get(session);
        if (projectId == null || userId == null) return;
        PresenceState st = presenceStates.get(session.getId());
        if (st == null) return;
        st.lastSeenTime = LocalDateTime.now();
        st.online = true;
        st.disconnectTime = null;

        UpdateWrapper<ProjectPresenceSession> uw = new UpdateWrapper<>();
        uw.eq("ws_session_id", session.getId());
        uw.set("last_seen_time", st.lastSeenTime);
        uw.set("disconnect_time", null);
        presenceSessionMapper.update(null, uw);

        broadcastPresence(projectId);
    }

    private void onPresenceJoin(Long projectId, Long userId, WebSocketSession session) {
        String username = null;
        try {
            User u = userMapper.selectById(userId);
            if (u != null) username = u.getUsername();
        } catch (Exception ignored) {
        }
        PresenceState st = new PresenceState();
        st.projectId = projectId;
        st.userId = userId;
        st.username = username;
        st.viewType = "PROJECT";
        st.viewId = projectId;
        st.lastSeenTime = LocalDateTime.now();
        st.isEditing = false;
        st.online = true;
        st.disconnectTime = null;
        presenceStates.put(session.getId(), st);

        try {
            ProjectPresenceSession row = new ProjectPresenceSession();
            row.setProjectId(projectId);
            row.setUserId(userId);
            row.setWsSessionId(session.getId());
            row.setViewType(st.viewType);
            row.setViewId(st.viewId);
            row.setLastSeenTime(st.lastSeenTime);
            row.setIsEditing(0);
            presenceSessionMapper.insert(row);
        } catch (Exception ignored) {
        }
    }

    private void onPresenceLeave(WebSocketSession session) {
        if (session == null) return;
        PresenceState st = presenceStates.get(session.getId());
        if (st == null) return;
        st.online = false;
        st.disconnectTime = LocalDateTime.now();
        UpdateWrapper<ProjectPresenceSession> uw = new UpdateWrapper<>();
        uw.eq("ws_session_id", session.getId());
        uw.set("disconnect_time", st.disconnectTime);
        uw.set("last_seen_time", st.disconnectTime);
        presenceSessionMapper.update(null, uw);
    }

    private void broadcastPresence(Long projectId) {
        if (projectId == null) return;
        try {
            LocalDateTime now = LocalDateTime.now();
            for (Map.Entry<String, PresenceState> en : presenceStates.entrySet()) {
                PresenceState s = en.getValue();
                if (s == null) continue;
                if (Boolean.TRUE.equals(s.online)) continue;
                LocalDateTime dt = s.disconnectTime;
                if (dt == null) continue;
                if (dt.plusSeconds(OFFLINE_GRACE_SECONDS).isBefore(now)) {
                    presenceStates.remove(en.getKey());
                }
            }

            Map<Long, PresenceMember> latest = new ConcurrentHashMap<>();
            for (PresenceState s : presenceStates.values()) {
                if (!projectId.equals(s.projectId)) continue;
                PresenceMember m = new PresenceMember(s.userId, s.username, s.viewType, s.viewId, s.lastSeenTime, s.online, s.isEditing);
                latest.merge(s.userId, m, (a, b) -> {
                    if (a.online() && !b.online()) return a;
                    if (!a.online() && b.online()) return b;
                    LocalDateTime ta = a.lastSeenTime();
                    LocalDateTime tb = b.lastSeenTime();
                    if (ta == null) return b;
                    if (tb == null) return a;
                    return tb.isAfter(ta) ? b : a;
                });
            }
            var members = latest.values().stream()
                    .sorted((a, b) -> {
                        LocalDateTime ta = a.lastSeenTime();
                        LocalDateTime tb = b.lastSeenTime();
                        if (ta == null && tb == null) return 0;
                        if (ta == null) return 1;
                        if (tb == null) return -1;
                        return tb.compareTo(ta);
                    })
                    .toList();
            String payloadJson = objectMapper.writeValueAsString(members);
            RealtimeServerMessage msg = new RealtimeServerMessage(null, projectId, null, "PRESENCE", payloadJson, LocalDateTime.now());
            broadcast(projectId, objectMapper.writeValueAsString(msg));
        } catch (Exception ignored) {
        }
    }

    private static class PresenceState {
        private Long projectId;
        private Long userId;
        private String username;
        private String viewType;
        private Long viewId;
        private LocalDateTime lastSeenTime;
        private boolean online;
        private boolean isEditing;
        private LocalDateTime disconnectTime;
    }

    public record PresenceMember(Long userId,
                                 String username,
                                 String viewType,
                                 Long viewId,
                                 LocalDateTime lastSeenTime,
                                 boolean online,
                                 boolean editing) {
    }
}
