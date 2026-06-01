package com.devtoolcopilot.ai.config;

import com.devtoolcopilot.config.JwtAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AiWebMvcConfig implements WebMvcConfigurer {
    @NonNull
    private final JwtAuthInterceptor jwtAuthInterceptor;

    public AiWebMvcConfig(@NonNull JwtAuthInterceptor jwtAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/ai/**");
    }
}
