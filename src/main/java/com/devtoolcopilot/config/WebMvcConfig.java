package com.devtoolcopilot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @NonNull
    private final JwtAuthInterceptor jwtAuthInterceptor;

    public WebMvcConfig(@NonNull JwtAuthInterceptor jwtAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/user/login",
                        "/api/user/register",
                        "/api/user/refresh",
                        "/api/user/logout",
                        "/api/user/password-reset/request",
                        "/api/user/password-reset/confirm",
                        "/api/user/email-verify/request",
                        "/api/user/email-verify/confirm"
                );
    }
}
