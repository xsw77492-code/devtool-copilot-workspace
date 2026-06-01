package com.devtoolcopilot.realtime.websocket;

public record RealtimeClientMessage(String op,
                                   Long projectId,
                                   String viewType,
                                   Long viewId,
                                   Boolean editing) {
}
