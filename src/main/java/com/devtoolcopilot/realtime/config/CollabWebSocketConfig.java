package com.devtoolcopilot.realtime.config;

import com.devtoolcopilot.realtime.websocket.CollabWebSocketHandler;
import com.devtoolcopilot.realtime.websocket.JwtQueryHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class CollabWebSocketConfig implements WebSocketConfigurer {
    private final CollabWebSocketHandler handler;
    private final JwtQueryHandshakeInterceptor handshakeInterceptor;

    public CollabWebSocketConfig(CollabWebSocketHandler handler, JwtQueryHandshakeInterceptor handshakeInterceptor) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/collab")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}

