package com.devtoolcopilot.project.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({DevtoolWebProperties.class, ProjectInviteProperties.class})
public class ProjectCollabConfig {
}

