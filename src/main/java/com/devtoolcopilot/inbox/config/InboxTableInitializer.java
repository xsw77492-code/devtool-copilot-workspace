package com.devtoolcopilot.inbox.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
public class InboxTableInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public InboxTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection()) {
            ensureInboxTable(c);
        } catch (Exception e) {
            System.err.println("INBOX_TABLE_INIT_FAILED: " + e.getMessage());
        }
    }

    private void ensureInboxTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS user_inbox_item (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  dedup_key VARCHAR(96) NOT NULL,
                  category VARCHAR(32) NOT NULL,
                  title VARCHAR(255) NOT NULL,
                  content TEXT NULL,
                  project_id BIGINT NULL,
                  task_id BIGINT NULL,
                  comment_id BIGINT NULL,
                  notification_id BIGINT NULL,
                  is_read TINYINT NOT NULL DEFAULT 0,
                  read_time DATETIME NULL,
                  is_handled TINYINT NOT NULL DEFAULT 0,
                  handled_time DATETIME NULL,
                  update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_user_dedup (user_id, dedup_key),
                  INDEX idx_user_handled_time (user_id, is_handled, create_time),
                  INDEX idx_user_read_time (user_id, is_read, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }
}

