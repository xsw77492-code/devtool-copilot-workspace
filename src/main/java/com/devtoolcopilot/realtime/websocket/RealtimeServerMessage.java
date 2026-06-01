package com.devtoolcopilot.realtime.websocket;

import java.time.LocalDateTime;

public record RealtimeServerMessage(Long eventId,
                                   Long projectId,
                                   Long actorUserId,
                                   String type,
                                   String payloadJson,
                                   LocalDateTime time) {
}

