package com.devtoolcopilot.config;

import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.common.exception.UnauthorizedException;
import com.devtoolcopilot.common.jwt.JwtTokenService;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {
    private final JwtTokenService jwtTokenService;

    public JwtAuthInterceptor(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        String token = resolveToken(request);
        Optional<Long> userIdOpt = jwtTokenService.parseUserId(token);
        if (userIdOpt.isEmpty()) {
            throw new UnauthorizedException("未登录或Token无效");
        }
        UserContext.setUserId(userIdOpt.get());
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                @Nullable Exception ex) {
        UserContext.clear();
    }

    private static String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring("Bearer ".length());
        }
        return request.getHeader("X-Token");
    }
}
