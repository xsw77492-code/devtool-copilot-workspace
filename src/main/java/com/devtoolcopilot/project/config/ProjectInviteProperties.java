package com.devtoolcopilot.project.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "devtool.project.invite")
public class ProjectInviteProperties {
    private Integer expireDays = 7;
    private Integer maxUses = 1;
}

