package com.devtoolcopilot.user.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.user.entity.User;
import com.devtoolcopilot.user.entity.UserRole;
import com.devtoolcopilot.user.service.UserService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {
    private final AuthSecurityProperties authSecurityProperties;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(AuthSecurityProperties authSecurityProperties,
                                    UserService userService,
                                    PasswordEncoder passwordEncoder) {
        this.authSecurityProperties = authSecurityProperties;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = authSecurityProperties.getBootstrapAdminUsername();
        String password = authSecurityProperties.getBootstrapAdminPassword();
        if (username == null || username.isBlank()) return;
        if (password == null || password.isBlank()) return;

        boolean exists = userService.exists(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (exists) return;

        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@devtoolcopilot.local");
        u.setPassword(passwordEncoder.encode(password));
        u.setRole(UserRole.ADMIN);
        u.setDisabled(0);
        u.setFailedLoginAttempts(0);
        userService.save(u);
    }
}

