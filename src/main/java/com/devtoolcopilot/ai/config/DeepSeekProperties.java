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
    /** 单次回复最大 token（可选） */
    private Integer maxTokens;

    /** 每月 token 配额（0 = 不限，套餐分级用） */
    private Long monthlyTokenQuota;

    /** 每日调用次数上限（0 = 不限） */
    private Long dailyCallLimit;
}
