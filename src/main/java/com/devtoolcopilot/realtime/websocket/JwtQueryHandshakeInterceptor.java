package com.devtoolcopilot.realtime.websocket;

import com.devtoolcopilot.common.jwt.JwtTokenService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;

@Component
public class JwtQueryHandshakeInterceptor implements HandshakeInterceptor {
    public static final String ATTR_USER_ID = "userId";
    private final JwtTokenService jwtTokenService;

    public JwtQueryHandshakeInterceptor(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("token");
        Optional<Long> userIdOpt = jwtTokenService.parseUserId(token);
        if (userIdOpt.isEmpty()) {
            return false;
        }
        attributes.put(ATTR_USER_ID, userIdOpt.get());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                              ServerHttpResponse response,
                              WebSocketHandler wsHandler,
                              Exception exception) {
    }
}

