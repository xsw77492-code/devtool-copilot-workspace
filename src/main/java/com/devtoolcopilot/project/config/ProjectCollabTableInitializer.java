package com.devtoolcopilot.project.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

@Component
public class ProjectCollabTableInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public ProjectCollabTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection()) {
            ensureProjectMemberTable(c);
            ensureProjectMemberColumns(c);
            ensureProjectInviteTable(c);
            ensureProjectInviteColumns(c);
            ensureProjectActivityTable(c);
            ensureUserProjectPinTable(c);
            ensureTeamGroupTable(c);
            ensureTeamGroupMemberTable(c);
            ensureTeamGroupInviteTable(c);
            ensureTeamGroupInviteColumns(c);
            ensureTeamGroupActivityTable(c);
            ensureGroupTaskTable(c);
            backfillOwnerAsMember(c);
            backfillDefaultTeamGroup(c);
        } catch (Exception e) {
            System.err.println("PROJECT_COLLAB_TABLE_INIT_FAILED: " + e.getMessage());
        }
    }

    private void ensureProjectMemberTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS project_member (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  project_id BIGINT NOT NULL,
                  user_id BIGINT NOT NULL,
                  role VARCHAR(16) NOT NULL DEFAULT 'VIEWER',
                  disabled TINYINT NOT NULL DEFAULT 0,
                  disabled_time DATETIME NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_project_user (project_id, user_id),
                  INDEX idx_project_member_project (project_id),
                  INDEX idx_project_member_user (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureProjectMemberColumns(Connection c) {
        ensureColumn(c, "project_member", "disabled",
                "ALTER TABLE project_member ADD COLUMN disabled TINYINT NOT NULL DEFAULT 0");
        ensureColumn(c, "project_member", "disabled_time",
                "ALTER TABLE project_member ADD COLUMN disabled_time DATETIME NULL");
    }

    private void ensureProjectInviteTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS project_invite (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  project_id BIGINT NOT NULL,
                  inviter_user_id BIGINT NOT NULL,
                  email VARCHAR(128) NOT NULL,
                  role VARCHAR(16) NOT NULL DEFAULT 'VIEWER',
                  token_hash CHAR(64) NOT NULL,
                  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
                  expire_time DATETIME NOT NULL,
                  max_uses INT NOT NULL DEFAULT 1,
                  used_count INT NOT NULL DEFAULT 0,
                  accepted_user_id BIGINT NULL,
                  handled_time DATETIME NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_invite_token_hash (token_hash),
                  INDEX idx_invite_project_status (project_id, status),
                  INDEX idx_invite_email_status (email, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureProjectInviteColumns(Connection c) {
        ensureColumn(c, "project_invite", "max_uses",
                "ALTER TABLE project_invite ADD COLUMN max_uses INT NOT NULL DEFAULT 1");
        ensureColumn(c, "project_invite", "used_count",
                "ALTER TABLE project_invite ADD COLUMN used_count INT NOT NULL DEFAULT 0");
    }

    private void ensureProjectActivityTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS project_activity (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  project_id BIGINT NOT NULL,
                  actor_user_id BIGINT NULL,
                  type VARCHAR(32) NOT NULL,
                  detail TEXT NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_activity_project_time (project_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureUserProjectPinTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS user_project_pin (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  project_id BIGINT NOT NULL,
                  sort BIGINT NOT NULL DEFAULT 0,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_pin_user_project (user_id, project_id),
                  INDEX idx_pin_user_sort (user_id, sort)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureTeamGroupTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS team_group (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  system_key VARCHAR(64) NULL,
                  name VARCHAR(64) NOT NULL,
                  owner_user_id BIGINT NOT NULL,
                  archived TINYINT NOT NULL DEFAULT 0,
                  sort BIGINT NOT NULL DEFAULT 0,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_team_group_system_key (system_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
        ensureColumn(c, "team_group", "archived",
                "ALTER TABLE team_group ADD COLUMN archived TINYINT NOT NULL DEFAULT 0");
    }

    private void ensureTeamGroupMemberTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS team_group_member (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  group_id BIGINT NOT NULL,
                  user_id BIGINT NOT NULL,
                  role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_team_group_user (group_id, user_id),
                  INDEX idx_team_group_member_user (user_id),
                  INDEX idx_team_group_member_group (group_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureTeamGroupInviteTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS team_group_invite (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  group_id BIGINT NOT NULL,
                  inviter_user_id BIGINT NOT NULL,
                  email VARCHAR(128) NOT NULL,
                  role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
                  token_hash CHAR(64) NULL,
                  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
                  expire_time DATETIME NULL,
                  accepted_user_id BIGINT NULL,
                  handled_time DATETIME NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_team_group_invite_group_status (group_id, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureTeamGroupInviteColumns(Connection c) {
        ensureColumn(c, "team_group_invite", "token_hash",
                "ALTER TABLE team_group_invite ADD COLUMN token_hash CHAR(64) NULL");
        ensureColumn(c, "team_group_invite", "expire_time",
                "ALTER TABLE team_group_invite ADD COLUMN expire_time DATETIME NULL");
        ensureColumn(c, "team_group_invite", "accepted_user_id",
                "ALTER TABLE team_group_invite ADD COLUMN accepted_user_id BIGINT NULL");
        ensureColumn(c, "team_group_invite", "handled_time",
                "ALTER TABLE team_group_invite ADD COLUMN handled_time DATETIME NULL");
    }

    private void ensureTeamGroupActivityTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS team_group_activity (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  group_id BIGINT NOT NULL,
                  actor_user_id BIGINT NULL,
                  type VARCHAR(32) NOT NULL,
                  detail VARCHAR(255) NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_team_group_activity_group_time (group_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureGroupTaskTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS group_task (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  group_id BIGINT NOT NULL,
                  title VARCHAR(200) NOT NULL,
                  description VARCHAR(1000) NULL,
                  priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
                  status VARCHAR(16) NOT NULL DEFAULT 'TODO',
                  assignee_id BIGINT NULL,
                  created_by BIGINT NOT NULL,
                  due_time DATETIME NULL,
                  done_time DATETIME NULL,
                  sort BIGINT NOT NULL DEFAULT 0,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_group_task_group (group_id),
                  INDEX idx_group_task_assignee (assignee_id),
                  INDEX idx_group_task_group_status (group_id, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void backfillOwnerAsMember(Connection c) {
        String sql = """
                INSERT INTO project_member(project_id, user_id, role)
                SELECT p.id, p.user_id, 'OWNER'
                FROM project p
                WHERE p.user_id IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM project_member pm
                    WHERE pm.project_id = p.id AND pm.user_id = p.user_id
                  )
                """;
        try (Statement s = c.createStatement()) {
            s.executeUpdate(sql);
        } catch (Exception e) {
            System.err.println("PROJECT_COLLAB_OWNER_BACKFILL_FAILED: " + e.getMessage());
        }
    }

    private void backfillDefaultTeamGroup(Connection c) {
        String insertGroup = """
                INSERT INTO team_group(system_key, name, owner_user_id)
                SELECT 'all-members', '团队全员', u.id
                FROM `user` u
                WHERE u.disabled = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM team_group tg WHERE tg.system_key = 'all-members'
                  )
                ORDER BY u.id
                LIMIT 1
                """;
        String insertMembers = """
                INSERT INTO team_group_member(group_id, user_id, role)
                SELECT tg.id,
                       u.id,
                       CASE WHEN u.id = tg.owner_user_id THEN 'OWNER' ELSE 'MEMBER' END
                FROM team_group tg
                JOIN `user` u ON u.disabled = 0
                WHERE tg.system_key = 'all-members'
                  AND NOT EXISTS (
                    SELECT 1 FROM team_group_member tgm
                    WHERE tgm.group_id = tg.id AND tgm.user_id = u.id
                  )
                """;
        String insertActivity = """
                INSERT INTO team_group_activity(group_id, actor_user_id, type, detail)
                SELECT tg.id, tg.owner_user_id, 'GROUP_CREATED', '团队全员已建立'
                FROM team_group tg
                WHERE tg.system_key = 'all-members'
                  AND NOT EXISTS (
                    SELECT 1 FROM team_group_activity tga WHERE tga.group_id = tg.id
                  )
                """;
        try (Statement s = c.createStatement()) {
            s.executeUpdate(insertGroup);
            s.executeUpdate(insertMembers);
            s.executeUpdate(insertActivity);
        } catch (Exception e) {
            System.err.println("TEAM_GROUP_BACKFILL_FAILED: " + e.getMessage());
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
            System.err.println("PROJECT_COLLAB_COLUMN_INIT_FAILED: " + table + "." + column + " " + e.getMessage());
        }
    }
}
