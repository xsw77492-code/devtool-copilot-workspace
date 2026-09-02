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
                  type VARCHAR(32) NOT NULL DEFAULT 'chat',
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_user_time (user_id, create_time),
                  INDEX idx_user_project_time (user_id, project_id, create_time),
                  INDEX idx_user_type (user_id, type)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        // 兼容旧表：若 type 列缺失则补充
        try (Connection c2 = dataSource.getConnection(); Statement s2 = c2.createStatement()) {
            s2.execute("ALTER TABLE ai_chat_history ADD COLUMN type VARCHAR(32) NOT NULL DEFAULT 'chat'");
        } catch (Exception ignored) {
            // 列已存在则忽略
        }
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(ddl);
        } catch (Exception e) {
            System.err.println("AI_HISTORY_TABLE_INIT_FAILED: " + e.getMessage());
        }
    }
}
