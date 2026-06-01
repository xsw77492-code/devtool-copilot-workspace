package com.devtoolcopilot.user.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

@Component
public class UserAuthTableInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public UserAuthTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection()) {
            ensureUserTable(c);
            ensureUserPreferenceTable(c);
            ensureRefreshTokenTable(c);
            ensurePasswordResetTokenTable(c);
            ensureLoginAuditTable(c);
            ensureEmailActionTable(c);
            ensureEmailVerifyTable(c);
        } catch (Exception e) {
            System.err.println("USER_AUTH_TABLE_INIT_FAILED: " + e.getMessage());
        }
    }

    private void ensureUserPreferenceTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS user_preference (
                  user_id BIGINT PRIMARY KEY,
                  accent_key VARCHAR(16) NOT NULL DEFAULT 'teal',
                  timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
                  week_start TINYINT NOT NULL DEFAULT 1,
                  reduce_motion TINYINT NOT NULL DEFAULT 0,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
        ensureColumn(c, "user_preference", "accent_key", "ALTER TABLE user_preference ADD COLUMN accent_key VARCHAR(16) NOT NULL DEFAULT 'teal'");
        ensureColumn(c, "user_preference", "timezone", "ALTER TABLE user_preference ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai'");
        ensureColumn(c, "user_preference", "week_start", "ALTER TABLE user_preference ADD COLUMN week_start TINYINT NOT NULL DEFAULT 1");
        ensureColumn(c, "user_preference", "reduce_motion", "ALTER TABLE user_preference ADD COLUMN reduce_motion TINYINT NOT NULL DEFAULT 0");
        ensureColumn(c, "user_preference", "create_time", "ALTER TABLE user_preference ADD COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");
        ensureColumn(c, "user_preference", "update_time",
                "ALTER TABLE user_preference ADD COLUMN update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
    }

    private void ensureUserTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS `user` (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  username VARCHAR(64) NOT NULL,
                  email VARCHAR(128) NOT NULL,
                  password VARCHAR(128) NOT NULL,
                  role VARCHAR(16) NOT NULL DEFAULT 'USER',
                  disabled TINYINT NOT NULL DEFAULT 0,
                  failed_login_attempts INT NOT NULL DEFAULT 0,
                  lock_until DATETIME NULL,
                  last_login_time DATETIME NULL,
                  last_login_ip VARCHAR(64) NULL,
                  last_login_user_agent VARCHAR(255) NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_user_username (username),
                  UNIQUE KEY uk_user_email (email)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }

        ensureColumn(c, "user", "email", "ALTER TABLE `user` ADD COLUMN email VARCHAR(128) NOT NULL DEFAULT ''");
        ensureColumn(c, "user", "email_verified", "ALTER TABLE `user` ADD COLUMN email_verified TINYINT NOT NULL DEFAULT 0");
        ensureColumn(c, "user", "role", "ALTER TABLE `user` ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER'");
        ensureColumn(c, "user", "disabled", "ALTER TABLE `user` ADD COLUMN disabled TINYINT NOT NULL DEFAULT 0");
        ensureColumn(c, "user", "failed_login_attempts", "ALTER TABLE `user` ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0");
        ensureColumn(c, "user", "lock_until", "ALTER TABLE `user` ADD COLUMN lock_until DATETIME NULL");
        ensureColumn(c, "user", "last_login_time", "ALTER TABLE `user` ADD COLUMN last_login_time DATETIME NULL");
        ensureColumn(c, "user", "last_login_ip", "ALTER TABLE `user` ADD COLUMN last_login_ip VARCHAR(64) NULL");
        ensureColumn(c, "user", "last_login_user_agent", "ALTER TABLE `user` ADD COLUMN last_login_user_agent VARCHAR(255) NULL");
        ensureColumn(c, "user", "update_time", "ALTER TABLE `user` ADD COLUMN update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");

        ensureIndex(c, "user", "uk_user_username", "ALTER TABLE `user` ADD UNIQUE KEY uk_user_username (username)");
        ensureIndex(c, "user", "uk_user_email", "ALTER TABLE `user` ADD UNIQUE KEY uk_user_email (email)");
    }

    private void ensureRefreshTokenTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS user_refresh_token (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  token_hash CHAR(64) NOT NULL,
                  device_name VARCHAR(128) NULL,
                  ip VARCHAR(64) NULL,
                  user_agent VARCHAR(255) NULL,
                  revoked TINYINT NOT NULL DEFAULT 0,
                  last_use_time DATETIME NULL,
                  expire_time DATETIME NOT NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_refresh_hash (token_hash),
                  INDEX idx_refresh_user_time (user_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensurePasswordResetTokenTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS user_password_reset_token (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  token_hash CHAR(64) NOT NULL,
                  used TINYINT NOT NULL DEFAULT 0,
                  expire_time DATETIME NOT NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_reset_hash (token_hash),
                  INDEX idx_reset_user_time (user_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureLoginAuditTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS user_login_audit (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  user_id BIGINT NULL,
                  username VARCHAR(64) NOT NULL,
                  success TINYINT NOT NULL,
                  fail_reason VARCHAR(64) NULL,
                  ip VARCHAR(64) NULL,
                  user_agent VARCHAR(255) NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_audit_user_time (user_id, create_time),
                  INDEX idx_audit_username_time (username, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureEmailActionTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS user_email_action (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  action VARCHAR(32) NOT NULL,
                  email VARCHAR(128) NOT NULL,
                  ip VARCHAR(64) NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_email_action_time (action, email, create_time),
                  INDEX idx_ip_action_time (action, ip, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureEmailVerifyTable(Connection c) throws Exception {
        String codeDdl = """
                CREATE TABLE IF NOT EXISTS user_email_verify_code (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  email VARCHAR(128) NOT NULL,
                  code_hash CHAR(64) NOT NULL,
                  used TINYINT NOT NULL DEFAULT 0,
                  expire_time DATETIME NOT NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_verify_code_email_time (email, create_time),
                  UNIQUE KEY uk_verify_code_hash (code_hash)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(codeDdl);
        }

        String tokenDdl = """
                CREATE TABLE IF NOT EXISTS user_email_verify_token (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  email VARCHAR(128) NOT NULL,
                  token_hash CHAR(64) NOT NULL,
                  used TINYINT NOT NULL DEFAULT 0,
                  expire_time DATETIME NOT NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_verify_token_email_time (email, create_time),
                  UNIQUE KEY uk_verify_token_hash (token_hash)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(tokenDdl);
        }
    }

    private void ensureColumn(Connection c, String table, String column, String ddl) {
        String sql = """
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            boolean exists = false;
            try (var rs = ps.executeQuery()) {
                if (rs.next()) exists = rs.getLong(1) > 0;
            }
            if (exists) return;
            try (Statement s = c.createStatement()) {
                s.execute(ddl);
            }
        } catch (Exception e) {
            System.err.println("USER_AUTH_COLUMN_INIT_FAILED: " + table + "." + column + " " + e.getMessage());
        }
    }

    private void ensureIndex(Connection c, String table, String indexName, String ddl) {
        String sql = """
                SELECT COUNT(*)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, indexName);
            boolean exists = false;
            try (var rs = ps.executeQuery()) {
                if (rs.next()) exists = rs.getLong(1) > 0;
            }
            if (exists) return;
            try (Statement s = c.createStatement()) {
                s.execute(ddl);
            }
        } catch (Exception e) {
            System.err.println("USER_AUTH_INDEX_INIT_FAILED: " + table + "." + indexName + " " + e.getMessage());
        }
    }
}
