package com.devtoolcopilot.realtime.websocket;

import com.devtoolcopilot.project.entity.ProjectMemberRole;
import com.devtoolcopilot.project.mapper.ProjectMemberMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class CollabWebSocketHandler extends TextWebSocketHandler {
    private static final String ATTR_PROJECT_ID = "projectId";

    private final ObjectMapper objectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final RealtimeSessionHub sessionHub;

    public CollabWebSocketHandler(ObjectMapper objectMapper,
                                  ProjectMemberMapper projectMemberMapper,
                                  RealtimeSessionHub sessionHub) {
        this.objectMapper = objectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.sessionHub = sessionHub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        session.sendMessage(new TextMessage("{\"type\":\"ready\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get(JwtQueryHandshakeInterceptor.ATTR_USER_ID);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        RealtimeClientMessage req;
        try {
            req = objectMapper.readValue(message.getPayload(), RealtimeClientMessage.class);
        } catch (Exception e) {
            return;
        }
        String op = req == null ? null : req.op();
        if (op == null || op.isBlank()) return;

        if ("SUBSCRIBE".equalsIgnoreCase(op)) {
            Long projectId = req.projectId();
            if (projectId == null) return;
            ProjectMemberRole role = projectMemberMapper.findRole(projectId, userId);
            if (role == null) {
                session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"FORBIDDEN\"}"));
                return;
            }
            Long old = (Long) session.getAttributes().get(ATTR_PROJECT_ID);
            if (old != null && !old.equals(projectId)) {
                sessionHub.unsubscribe(old, session);
            }
            session.getAttributes().put(ATTR_PROJECT_ID, projectId);
            sessionHub.subscribe(projectId, userId, session);
            session.sendMessage(new TextMessage("{\"type\":\"subscribed\",\"projectId\":" + projectId + "}"));
            if (req.viewType() != null && !req.viewType().isBlank()) {
                sessionHub.updatePresenceView(session, req.viewType(), req.viewId());
            }
            return;
        }

        if ("UNSUBSCRIBE".equalsIgnoreCase(op)) {
            Long old = (Long) session.getAttributes().get(ATTR_PROJECT_ID);
            if (old != null) {
                sessionHub.unsubscribe(old, session);
            }
            session.getAttributes().remove(ATTR_PROJECT_ID);
            return;
        }

        if ("VIEW".equalsIgnoreCase(op) || "PRESENCE".equalsIgnoreCase(op)) {
            Long projectId = (Long) session.getAttributes().get(ATTR_PROJECT_ID);
            if (projectId == null) return;
            ProjectMemberRole role = projectMemberMapper.findRole(projectId, userId);
            if (role == null) {
                session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"FORBIDDEN\"}"));
                return;
            }
            String viewType = req.viewType();
            if (viewType == null || viewType.isBlank()) return;
            sessionHub.updatePresenceView(session, viewType, req.viewId());
        }

        if ("EDIT".equalsIgnoreCase(op)) {
            Long projectId = (Long) session.getAttributes().get(ATTR_PROJECT_ID);
            if (projectId == null) return;
            ProjectMemberRole role = projectMemberMapper.findRole(projectId, userId);
            if (role == null) {
                session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"FORBIDDEN\"}"));
                return;
            }
            sessionHub.updatePresenceEditing(session, req.editing());
        }

        if ("PING".equalsIgnoreCase(op)) {
            Long projectId = (Long) session.getAttributes().get(ATTR_PROJECT_ID);
            if (projectId == null) return;
            ProjectMemberRole role = projectMemberMapper.findRole(projectId, userId);
            if (role == null) return;
            sessionHub.updatePresenceHeartbeat(session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionHub.removeSession(session);
    }
}
