package com.devtoolcopilot.asset.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

@Component
public class AssetTableInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public AssetTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection()) {
            ensureTable(c);
            ensureIndexes(c);
        } catch (Exception e) {
            System.err.println("ASSET_TABLE_INIT_FAILED: " + e.getMessage());
        }
    }

    private void ensureTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS project_asset (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  project_id BIGINT NOT NULL,
                  user_id BIGINT NOT NULL,
                  kind VARCHAR(32) NOT NULL,
                  name VARCHAR(255) NOT NULL,
                  ext VARCHAR(16) NULL,
                  content_type VARCHAR(128) NULL,
                  size_bytes BIGINT NOT NULL DEFAULT 0,
                  storage_key VARCHAR(64) NOT NULL,
                  storage_path VARCHAR(512) NOT NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_asset_project (project_id, id),
                  INDEX idx_asset_user (user_id, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureIndexes(Connection c) {
        ensureIndex(c, "project_asset", "idx_asset_kind_project", "ALTER TABLE project_asset ADD INDEX idx_asset_kind_project (kind, project_id, id)");
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
            System.err.println("ASSET_INDEX_INIT_FAILED: " + table + "." + indexName + " " + e.getMessage());
        }
    }
}

