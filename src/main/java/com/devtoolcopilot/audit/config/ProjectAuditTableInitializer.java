package com.devtoolcopilot.audit.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

@Component
public class ProjectAuditTableInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public ProjectAuditTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection()) {
            ensureAuditTable(c);
            ensureAuditIndexes(c);
        } catch (Exception e) {
            System.err.println("PROJECT_AUDIT_TABLE_INIT_FAILED: " + e.getMessage());
        }
    }

    private void ensureAuditTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS project_audit_log (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  project_id BIGINT NOT NULL,
                  actor_user_id BIGINT NOT NULL,
                  action VARCHAR(64) NOT NULL,
                  target_type VARCHAR(32) NULL,
                  target_id BIGINT NULL,
                  summary VARCHAR(255) NULL,
                  detail TEXT NULL,
                  ip VARCHAR(64) NULL,
                  user_agent VARCHAR(512) NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureAuditIndexes(Connection c) {
        ensureIndex(c, "project_audit_log", "idx_audit_project_time",
                "CREATE INDEX idx_audit_project_time ON project_audit_log (project_id, create_time, id)");
        ensureIndex(c, "project_audit_log", "idx_audit_project_action_time",
                "CREATE INDEX idx_audit_project_action_time ON project_audit_log (project_id, action, create_time, id)");
        ensureIndex(c, "project_audit_log", "idx_audit_project_actor_time",
                "CREATE INDEX idx_audit_project_actor_time ON project_audit_log (project_id, actor_user_id, create_time, id)");
        ensureIndex(c, "project_audit_log", "idx_audit_target",
                "CREATE INDEX idx_audit_target ON project_audit_log (project_id, target_type, target_id, id)");
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
            System.err.println("PROJECT_AUDIT_INDEX_INIT_FAILED: " + table + "." + indexName + " " + e.getMessage());
        }
    }
}

