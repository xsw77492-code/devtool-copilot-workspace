package com.devtoolcopilot.ai.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
public class AiUsageTableInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public AiUsageTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ai_usage (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  project_id BIGINT,
                  type VARCHAR(32) NOT NULL DEFAULT 'chat',
                  prompt_tokens BIGINT NOT NULL DEFAULT 0,
                  completion_tokens BIGINT NOT NULL DEFAULT 0,
                  total_tokens BIGINT NOT NULL DEFAULT 0,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_user_time (user_id, create_time),
                  INDEX idx_type (type)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        }
    }
}
