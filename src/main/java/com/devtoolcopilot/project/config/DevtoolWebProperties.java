package com.devtoolcopilot.project.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "devtool.web")
public class DevtoolWebProperties {
    private String baseUrl;
}

