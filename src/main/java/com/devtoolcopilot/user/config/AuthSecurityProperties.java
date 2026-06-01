package com.devtoolcopilot.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "devtool.auth")
public class AuthSecurityProperties {
    private int maxFailedLoginAttempts = 5;
    private int lockMinutes = 15;
    private int refreshTokenExpireDays = 30;
    private int passwordResetExpireMinutes = 30;

    private String passwordResetLinkBase = "http://localhost:5173/reset-password?token=";

    private boolean requireEmailVerification = false;
    private int emailVerifyExpireMinutes = 10;

    private int passwordResetMaxPerEmailPerHour = 3;
    private int passwordResetMaxPerIpPerHour = 10;

    private int loginFailMaxPerIpIn10Min = 20;

    private String bootstrapAdminUsername;
    private String bootstrapAdminPassword;
}
