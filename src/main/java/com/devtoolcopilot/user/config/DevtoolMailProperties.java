package com.devtoolcopilot.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "devtool.mail")
public class DevtoolMailProperties {
    private String from;
}

