package com.devtoolcopilot.release.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

@Component
public class ReleaseTableInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public ReleaseTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection()) {
            ensureTable(c);
            ensureColumns(c);
            ensureIndexes(c);
        } catch (Exception e) {
            System.err.println("RELEASE_TABLE_INIT_FAILED: " + e.getMessage());
        }
    }

    private void ensureTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS project_release (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  project_id BIGINT NOT NULL,
                  milestone_id BIGINT NULL,
                  user_id BIGINT NOT NULL,
                  version VARCHAR(64) NOT NULL,
                  summary TEXT NULL,
                  notes_asset_id BIGINT NULL,
                  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
                  published_time DATETIME NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_release_project_time (project_id, create_time, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureColumns(Connection c) {
        ensureColumn(c, "project_release", "milestone_id", "ALTER TABLE project_release ADD COLUMN milestone_id BIGINT NULL");
        ensureColumn(c, "project_release", "summary", "ALTER TABLE project_release ADD COLUMN summary TEXT NULL");
        ensureColumn(c, "project_release", "notes_asset_id", "ALTER TABLE project_release ADD COLUMN notes_asset_id BIGINT NULL");
        ensureColumn(c, "project_release", "published_time", "ALTER TABLE project_release ADD COLUMN published_time DATETIME NULL");
        ensureColumn(c, "project_release", "status", "ALTER TABLE project_release ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'");
    }

    private void ensureIndexes(Connection c) {
        ensureIndex(c, "project_release", "idx_release_project_status", "ALTER TABLE project_release ADD INDEX idx_release_project_status (project_id, status, id)");
        ensureIndex(c, "project_release", "idx_release_milestone", "ALTER TABLE project_release ADD INDEX idx_release_milestone (milestone_id, id)");
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
            System.err.println("RELEASE_COLUMN_INIT_FAILED: " + table + "." + column + " " + e.getMessage());
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
            System.err.println("RELEASE_INDEX_INIT_FAILED: " + table + "." + indexName + " " + e.getMessage());
        }
    }
}

