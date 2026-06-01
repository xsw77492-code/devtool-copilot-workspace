package com.devtoolcopilot.notification.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
public class NotificationSettingsTableInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public NotificationSettingsTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection()) {
            ensureSettingTable(c);
            ensureTypePrefTable(c);
        } catch (Exception e) {
            System.err.println("NOTIFICATION_SETTINGS_TABLE_INIT_FAILED: " + e.getMessage());
        }
    }

    private void ensureSettingTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS user_notification_setting (
                  user_id BIGINT PRIMARY KEY,
                  dnd_enabled TINYINT NOT NULL DEFAULT 0,
                  dnd_start_minute INT NOT NULL DEFAULT 0,
                  dnd_end_minute INT NOT NULL DEFAULT 0,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureTypePrefTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS user_notification_type_pref (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  type VARCHAR(32) NOT NULL,
                  enabled TINYINT NOT NULL DEFAULT 1,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_user_type (user_id, type),
                  INDEX idx_user_type_time (user_id, update_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }
}

