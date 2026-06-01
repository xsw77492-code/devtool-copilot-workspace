package com.devtoolcopilot.notification.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

@Component
public class NotificationTableInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public NotificationTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection()) {
            ensureNotificationTable(c);
            ensureNotificationColumns(c);
            ensureNotificationIndexes(c);
        } catch (Exception e) {
            System.err.println("NOTIFICATION_TABLE_INIT_FAILED: " + e.getMessage());
        }
    }

    private void ensureNotificationTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS user_notification (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  type VARCHAR(32) NOT NULL,
                  title VARCHAR(128) NOT NULL,
                  content TEXT NULL,
                  data_json TEXT NULL,
                  is_read TINYINT NOT NULL DEFAULT 0,
                  read_time DATETIME NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_notification_user_id (user_id, id),
                  INDEX idx_notification_user_unread (user_id, is_read, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureNotificationColumns(Connection c) {
        ensureColumn(c, "user_notification", "project_id", "ALTER TABLE user_notification ADD COLUMN project_id BIGINT NULL");
        ensureColumn(c, "user_notification", "task_id", "ALTER TABLE user_notification ADD COLUMN task_id BIGINT NULL");
        ensureColumn(c, "user_notification", "comment_id", "ALTER TABLE user_notification ADD COLUMN comment_id BIGINT NULL");
        ensureColumn(c, "user_notification", "group_key", "ALTER TABLE user_notification ADD COLUMN group_key VARCHAR(64) NULL");
        ensureColumn(c, "user_notification", "agg_count", "ALTER TABLE user_notification ADD COLUMN agg_count INT NOT NULL DEFAULT 1");
        ensureColumn(c, "user_notification", "update_time", "ALTER TABLE user_notification ADD COLUMN update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
    }

    private void ensureNotificationIndexes(Connection c) {
        ensureIndex(c, "user_notification", "idx_notification_user_project", "ALTER TABLE user_notification ADD INDEX idx_notification_user_project (user_id, project_id, id)");
        ensureIndex(c, "user_notification", "idx_notification_user_group_unread", "ALTER TABLE user_notification ADD INDEX idx_notification_user_group_unread (user_id, group_key, is_read, update_time)");
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
            System.err.println("NOTIFICATION_COLUMN_INIT_FAILED: " + table + "." + column + " " + e.getMessage());
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
            System.err.println("NOTIFICATION_INDEX_INIT_FAILED: " + table + "." + indexName + " " + e.getMessage());
        }
    }
}
