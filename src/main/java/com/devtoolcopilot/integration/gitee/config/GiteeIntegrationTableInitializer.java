package com.devtoolcopilot.integration.gitee.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
public class GiteeIntegrationTableInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public GiteeIntegrationTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                    CREATE TABLE IF NOT EXISTS gitee_repo_config (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT,
                      user_id BIGINT NOT NULL,
                      project_id BIGINT NOT NULL,
                      owner VARCHAR(128) NOT NULL,
                      repo VARCHAR(128) NOT NULL,
                      access_token VARCHAR(255) NOT NULL,
                      create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      UNIQUE KEY uk_user_project (user_id, project_id),
                      INDEX idx_user (user_id),
                      INDEX idx_project (project_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            s.execute("""
                    CREATE TABLE IF NOT EXISTS task_pr_link (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT,
                      user_id BIGINT NOT NULL,
                      project_id BIGINT NOT NULL,
                      task_id BIGINT NOT NULL,
                      pr_number INT NOT NULL,
                      pr_url VARCHAR(512) NULL,
                      source VARCHAR(16) NOT NULL,
                      create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      UNIQUE KEY uk_task_pr (task_id, pr_number),
                      INDEX idx_project (project_id),
                      INDEX idx_task (task_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        } catch (Exception e) {
            System.err.println("GITEE_TABLE_INIT_FAILED: " + e.getMessage());
        }
    }
}

