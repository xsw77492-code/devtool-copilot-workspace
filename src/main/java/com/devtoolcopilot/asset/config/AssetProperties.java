package com.devtoolcopilot.asset.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "devtool.asset")
public class AssetProperties {
    private String baseDir;
}

