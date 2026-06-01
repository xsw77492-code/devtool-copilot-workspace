package com.devtoolcopilot.ai.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
public class AiChatHistoryTableInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public AiChatHistoryTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        String ddl = """
                CREATE TABLE IF NOT EXISTS ai_chat_history (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  project_id BIGINT NULL,
                  prompt TEXT NOT NULL,
                  response LONGTEXT NOT NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_user_time (user_id, create_time),
                  INDEX idx_user_project_time (user_id, project_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(ddl);
        } catch (Exception e) {
            System.err.println("AI_HISTORY_TABLE_INIT_FAILED: " + e.getMessage());
        }
    }
}
