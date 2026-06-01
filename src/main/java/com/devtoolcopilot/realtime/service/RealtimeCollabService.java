package com.devtoolcopilot.realtime.service;

public interface RealtimeCollabService {
    void broadcast(Long projectId, Long actorUserId, String type, Object payload);
}

