package com.devtoolcopilot.milestone.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

@Component
public class MilestoneTableInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public MilestoneTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection()) {
            ensureTable(c);
            ensureColumns(c);
            ensureIndexes(c);
        } catch (Exception e) {
            System.err.println("MILESTONE_TABLE_INIT_FAILED: " + e.getMessage());
        }
    }

    private void ensureTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS project_milestone (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  project_id BIGINT NOT NULL,
                  user_id BIGINT NOT NULL,
                  name VARCHAR(128) NOT NULL,
                  description TEXT NULL,
                  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
                  due_time DATETIME NULL,
                  published_time DATETIME NULL,
                  archived_time DATETIME NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_milestone_project_time (project_id, create_time, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureColumns(Connection c) {
        ensureColumn(c, "project_milestone", "release_asset_id", "ALTER TABLE project_milestone ADD COLUMN release_asset_id BIGINT NULL");
    }

    private void ensureIndexes(Connection c) {
        ensureIndex(c, "project_milestone", "idx_milestone_project_status", "ALTER TABLE project_milestone ADD INDEX idx_milestone_project_status (project_id, status, id)");
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
            System.err.println("MILESTONE_COLUMN_INIT_FAILED: " + table + "." + column + " " + e.getMessage());
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
            System.err.println("MILESTONE_INDEX_INIT_FAILED: " + table + "." + indexName + " " + e.getMessage());
        }
    }
}
