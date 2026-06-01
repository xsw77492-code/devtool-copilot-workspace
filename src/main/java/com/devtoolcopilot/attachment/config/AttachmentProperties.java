package com.devtoolcopilot.attachment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "devtool.attachment")
public class AttachmentProperties {
    private String baseDir;
    private Integer maxSizeMb;
}

