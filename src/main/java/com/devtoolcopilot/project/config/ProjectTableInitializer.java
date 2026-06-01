package com.devtoolcopilot.project.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

@Component
public class ProjectTableInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public ProjectTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection()) {
            ensureColumns(c);
            ensureIndexes(c);
        } catch (Exception e) {
            System.err.println("PROJECT_TABLE_INIT_FAILED: " + e.getMessage());
        }
    }

    private void ensureColumns(Connection c) {
        ensureColumn(c, "project", "archived", "ALTER TABLE project ADD COLUMN archived TINYINT NOT NULL DEFAULT 0");
        ensureColumn(c, "project", "archived_time", "ALTER TABLE project ADD COLUMN archived_time DATETIME NULL");
    }

    private void ensureIndexes(Connection c) {
        ensureIndex(c, "project", "idx_project_archived", "CREATE INDEX idx_project_archived ON project (archived, id)");
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
            System.err.println("PROJECT_COLUMN_INIT_FAILED: " + table + "." + column + " " + e.getMessage());
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
            System.err.println("PROJECT_INDEX_INIT_FAILED: " + table + "." + indexName + " " + e.getMessage());
        }
    }
}

