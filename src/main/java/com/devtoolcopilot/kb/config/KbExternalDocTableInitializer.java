package com.devtoolcopilot.kb.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
public class KbExternalDocTableInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public KbExternalDocTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        String ddl = """
                CREATE TABLE IF NOT EXISTS kb_external_doc (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  project_id BIGINT NULL,
                  title VARCHAR(255) NOT NULL,
                  url VARCHAR(1024) NULL,
                  content LONGTEXT NOT NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_kb_user_time (user_id, update_time),
                  INDEX idx_kb_user_project_time (user_id, project_id, update_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(ddl);
        } catch (Exception e) {
            System.err.println("KB_EXTERNAL_DOC_TABLE_INIT_FAILED: " + e.getMessage());
        }
    }
}

