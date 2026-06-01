package com.devtoolcopilot.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.deepseek")
public class DeepSeekProperties {
    private String baseUrl;
    private String apiKey;
    private String model;
    private double temperature;
    private int timeoutMs;
}
