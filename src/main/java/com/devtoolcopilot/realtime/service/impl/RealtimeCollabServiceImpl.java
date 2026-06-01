package com.devtoolcopilot.realtime.service.impl;

import com.devtoolcopilot.realtime.entity.ProjectRealtimeEvent;
import com.devtoolcopilot.realtime.mapper.ProjectRealtimeEventMapper;
import com.devtoolcopilot.realtime.service.RealtimeCollabService;
import com.devtoolcopilot.realtime.websocket.RealtimeServerMessage;
import com.devtoolcopilot.realtime.websocket.RealtimeSessionHub;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class RealtimeCollabServiceImpl implements RealtimeCollabService {
    private final ProjectRealtimeEventMapper eventMapper;
    private final ObjectMapper objectMapper;
    private final RealtimeSessionHub sessionHub;

    public RealtimeCollabServiceImpl(ProjectRealtimeEventMapper eventMapper,
                                    ObjectMapper objectMapper,
                                    RealtimeSessionHub sessionHub) {
        this.eventMapper = eventMapper;
        this.objectMapper = objectMapper;
        this.sessionHub = sessionHub;
    }

    @Override
    public void broadcast(Long projectId, Long actorUserId, String type, Object payload) {
        if (projectId == null || type == null || type.isBlank()) return;
        String payloadJson = null;
        try {
            if (payload instanceof String s) {
                payloadJson = s;
            } else if (payload != null) {
                payloadJson = objectMapper.writeValueAsString(payload);
            }
        } catch (Exception ignored) {
        }

        ProjectRealtimeEvent e = new ProjectRealtimeEvent();
        e.setProjectId(projectId);
        e.setActorUserId(actorUserId);
        e.setType(type);
        e.setPayloadJson(payloadJson);
        try {
            eventMapper.insert(e);
        } catch (Exception ignored) {
        }

        try {
            RealtimeServerMessage msg = new RealtimeServerMessage(e.getId(), projectId, actorUserId, type, payloadJson, e.getCreateTime());
            sessionHub.broadcast(projectId, objectMapper.writeValueAsString(msg));
        } catch (Exception ignored) {
        }
    }
}

