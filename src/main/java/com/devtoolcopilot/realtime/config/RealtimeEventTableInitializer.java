package com.devtoolcopilot.realtime.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
public class RealtimeEventTableInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public RealtimeEventTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection()) {
            ensureEventTable(c);
        } catch (Exception e) {
            System.err.println("REALTIME_EVENT_TABLE_INIT_FAILED: " + e.getMessage());
        }
    }

    private void ensureEventTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS project_realtime_event (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  project_id BIGINT NOT NULL,
                  actor_user_id BIGINT NULL,
                  type VARCHAR(64) NOT NULL,
                  payload_json TEXT NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_realtime_project_id (project_id, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }
}

