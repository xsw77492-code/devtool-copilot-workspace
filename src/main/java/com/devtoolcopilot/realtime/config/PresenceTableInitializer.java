package com.devtoolcopilot.realtime.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

@Component
public class PresenceTableInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public PresenceTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection()) {
            ensurePresenceTable(c);
            ensurePresenceColumns(c);
        } catch (Exception e) {
            System.err.println("PRESENCE_TABLE_INIT_FAILED: " + e.getMessage());
        }
    }

    private void ensurePresenceTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS project_presence_session (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  project_id BIGINT NOT NULL,
                  user_id BIGINT NOT NULL,
                  ws_session_id VARCHAR(64) NOT NULL,
                  view_type VARCHAR(32) NULL,
                  view_id BIGINT NULL,
                  last_seen_time DATETIME NULL,
                  is_editing TINYINT NOT NULL DEFAULT 0,
                  connect_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  disconnect_time DATETIME NULL,
                  INDEX idx_presence_project (project_id, id),
                  INDEX idx_presence_user (user_id, id),
                  UNIQUE KEY uk_presence_ws_session (ws_session_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensurePresenceColumns(Connection c) {
        ensureColumn(c, "project_presence_session", "is_editing",
                "ALTER TABLE project_presence_session ADD COLUMN is_editing TINYINT NOT NULL DEFAULT 0");
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
            System.err.println("PRESENCE_COLUMN_INIT_FAILED: " + table + "." + column + " " + e.getMessage());
        }
    }
}
