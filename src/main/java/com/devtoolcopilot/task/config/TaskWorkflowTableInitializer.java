package com.devtoolcopilot.task.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

@Component
public class TaskWorkflowTableInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public TaskWorkflowTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection()) {
            ensureTaskColumns(c);
            ensureTaskIndexes(c);
            backfillTaskBoardSort(c);
            backfillTaskType(c);
            ensureTimelineTable(c);
            ensureCommentTable(c);
            ensureDeliverableTable(c);
            ensureChecklistTable(c);
            ensureProjectTaskRuleTable(c);
            ensureBoardViewTable(c);
            ensureTaskTemplateTable(c);
            ensureTaskFollowTable(c);
        } catch (Exception e) {
            System.err.println("TASK_WORKFLOW_TABLE_INIT_FAILED: " + e.getMessage());
        }
    }

    private void ensureTaskColumns(Connection c) {
        ensureColumn(c, "task", "description", "ALTER TABLE `task` ADD COLUMN description TEXT NULL");
        ensureColumn(c, "task", "acceptance_criteria", "ALTER TABLE `task` ADD COLUMN acceptance_criteria TEXT NULL");
        ensureColumn(c, "task", "priority", "ALTER TABLE `task` ADD COLUMN priority VARCHAR(16) NULL");
        ensureColumn(c, "task", "tags", "ALTER TABLE `task` ADD COLUMN tags VARCHAR(512) NULL");
        ensureColumn(c, "task", "assignee", "ALTER TABLE `task` ADD COLUMN assignee VARCHAR(64) NULL");
        ensureColumn(c, "task", "assignee_id", "ALTER TABLE `task` ADD COLUMN assignee_id BIGINT NULL");
        ensureColumn(c, "task", "due_time", "ALTER TABLE `task` ADD COLUMN due_time DATETIME NULL");
        ensureColumn(c, "task", "due_reminded_time", "ALTER TABLE `task` ADD COLUMN due_reminded_time DATETIME NULL");
        ensureColumn(c, "task", "board_sort", "ALTER TABLE `task` ADD COLUMN board_sort BIGINT NOT NULL DEFAULT 0");
        ensureColumn(c, "task", "started_time", "ALTER TABLE `task` ADD COLUMN started_time DATETIME NULL");
        ensureColumn(c, "task", "done_time", "ALTER TABLE `task` ADD COLUMN done_time DATETIME NULL");
        ensureColumn(c, "task", "milestone_id", "ALTER TABLE `task` ADD COLUMN milestone_id BIGINT NULL");
        ensureColumn(c, "task", "parent_task_id", "ALTER TABLE `task` ADD COLUMN parent_task_id BIGINT NULL");
        ensureColumn(c, "task", "type", "ALTER TABLE `task` ADD COLUMN type VARCHAR(16) NULL");
        ensureColumn(c, "task", "update_time", "ALTER TABLE `task` ADD COLUMN update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
    }

    private void ensureTaskIndexes(Connection c) {
        ensureIndex(
                c,
                "task",
                "idx_task_project_status_sort",
                "CREATE INDEX idx_task_project_status_sort ON `task` (project_id, status, board_sort, id)"
        );
        ensureIndex(
                c,
                "task",
                "idx_task_project_parent",
                "CREATE INDEX idx_task_project_parent ON `task` (project_id, parent_task_id, id)"
        );
        ensureIndex(
                c,
                "task",
                "idx_task_project_milestone_status",
                "CREATE INDEX idx_task_project_milestone_status ON `task` (project_id, milestone_id, status, id)"
        );
    }

    private void backfillTaskBoardSort(Connection c) {
        try (Statement s = c.createStatement()) {
            s.execute("UPDATE `task` SET board_sort = id WHERE board_sort IS NULL OR board_sort = 0");
        } catch (Exception e) {
            System.err.println("TASK_WORKFLOW_BACKFILL_FAILED: task.board_sort " + e.getMessage());
        }
    }

    private void backfillTaskType(Connection c) {
        try (Statement s = c.createStatement()) {
            s.execute("UPDATE `task` SET type = 'TASK' WHERE type IS NULL OR type = ''");
        } catch (Exception e) {
            System.err.println("TASK_WORKFLOW_BACKFILL_FAILED: task.type " + e.getMessage());
        }
    }

    private void ensureTimelineTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS task_timeline (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  project_id BIGINT NOT NULL,
                  task_id BIGINT NOT NULL,
                  type VARCHAR(32) NOT NULL,
                  title VARCHAR(64) NULL,
                  detail TEXT NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_task_time (task_id, create_time),
                  INDEX idx_user_project_time (user_id, project_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureCommentTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS task_comment (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  project_id BIGINT NOT NULL,
                  task_id BIGINT NOT NULL,
                  user_id BIGINT NOT NULL,
                  content TEXT NOT NULL,
                  reply_to_id BIGINT NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_task_comment_task_time (task_id, create_time),
                  INDEX idx_task_comment_project_time (project_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureDeliverableTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS task_deliverable (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  project_id BIGINT NOT NULL,
                  task_id BIGINT NOT NULL,
                  user_id BIGINT NOT NULL,
                  type VARCHAR(16) NOT NULL,
                  title VARCHAR(255) NOT NULL,
                  url VARCHAR(1024) NULL,
                  content MEDIUMTEXT NULL,
                  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
                  sort BIGINT NOT NULL DEFAULT 0,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_deliverable_task_sort (task_id, status, sort, id),
                  INDEX idx_deliverable_project_time (project_id, create_time, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureChecklistTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS task_checklist_item (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  project_id BIGINT NOT NULL,
                  task_id BIGINT NOT NULL,
                  user_id BIGINT NOT NULL,
                  content VARCHAR(512) NOT NULL,
                  is_done TINYINT NOT NULL DEFAULT 0,
                  done_time DATETIME NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_checklist_task_time (task_id, create_time, id),
                  INDEX idx_checklist_task_done_time (task_id, is_done, update_time, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureProjectTaskRuleTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS project_task_rule (
                  project_id BIGINT PRIMARY KEY,
                  require_checklist_done_for_done TINYINT NOT NULL DEFAULT 0,
                  update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureBoardViewTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS task_board_view (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  project_id BIGINT NOT NULL,
                  user_id BIGINT NOT NULL,
                  name VARCHAR(64) NOT NULL,
                  filters_json TEXT NOT NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_view_project_user_time (project_id, user_id, update_time),
                  UNIQUE KEY uk_view_project_user_name (project_id, user_id, name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
        ensureColumn(c, "task_board_view", "color", "ALTER TABLE task_board_view ADD COLUMN color VARCHAR(16) NULL");
    }

    private void ensureTaskTemplateTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS task_template (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  project_id BIGINT NULL,
                  user_id BIGINT NOT NULL,
                  name VARCHAR(64) NOT NULL,
                  payload_json TEXT NOT NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_tpl_project_user_time (project_id, user_id, update_time),
                  UNIQUE KEY uk_tpl_project_user_name (project_id, user_id, name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureTaskFollowTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS task_follow (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  task_id BIGINT NOT NULL,
                  user_id BIGINT NOT NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_task_user (task_id, user_id),
                  INDEX idx_user_time (user_id, create_time),
                  INDEX idx_task_time (task_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
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
            System.err.println("TASK_WORKFLOW_COLUMN_INIT_FAILED: " + table + "." + column + " " + e.getMessage());
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
            System.err.println("TASK_WORKFLOW_INDEX_INIT_FAILED: " + table + "." + indexName + " " + e.getMessage());
        }
    }
}
