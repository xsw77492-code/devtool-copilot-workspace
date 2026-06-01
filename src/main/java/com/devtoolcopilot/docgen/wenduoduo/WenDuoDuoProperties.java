package com.devtoolcopilot.docgen.wenduoduo;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "docgen.wenduoduo")
public class WenDuoDuoProperties {
    private String baseUrl;
    private String apiKey;
    private boolean useApiToken;
    private String templateId;
    private int createTaskType;
    private int timeoutMs;
    private int pollIntervalMs;
    private int pollTimeoutMs;
    private String queryTaskPath;
}
