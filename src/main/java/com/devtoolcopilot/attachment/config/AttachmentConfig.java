package com.devtoolcopilot.attachment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AttachmentProperties.class)
public class AttachmentConfig {
}

