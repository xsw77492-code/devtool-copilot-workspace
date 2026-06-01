package com.devtoolcopilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "devtool.jwt")
public class JwtProperties {
    private String issuer;
    private String secret;
    private long expireSeconds;
}
