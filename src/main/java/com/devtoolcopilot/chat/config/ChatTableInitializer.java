package com.devtoolcopilot.chat.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

@Component
public class ChatTableInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public ChatTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection()) {
            ensureConversationTable(c);
            ensureConversationMemberTable(c);
            ensureMessageTable(c);
            ensureAttachmentTable(c);
            ensureUserProfileColumns(c);
        } catch (Exception e) {
            System.err.println("CHAT_TABLE_INIT_FAILED: " + e.getMessage());
        }
    }

    private void ensureConversationTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS chat_conversation (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  type VARCHAR(16) NOT NULL,
                  name VARCHAR(64) NULL,
                  creator_id BIGINT NOT NULL,
                  last_message_id BIGINT NULL,
                  last_message_at DATETIME NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_chat_conv_creator (creator_id),
                  INDEX idx_chat_conv_last_at (last_message_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
        ensureColumn(c, "chat_conversation", "announcement",
                "ALTER TABLE chat_conversation ADD COLUMN announcement TEXT NULL");
    }

    private void ensureConversationMemberTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS chat_conversation_member (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  conversation_id BIGINT NOT NULL,
                  user_id BIGINT NOT NULL,
                  role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
                  last_read_message_id BIGINT NULL,
                  muted TINYINT NOT NULL DEFAULT 0,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_chat_conv_user (conversation_id, user_id),
                  INDEX idx_chat_cm_user (user_id),
                  INDEX idx_chat_cm_conv (conversation_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureMessageTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS chat_message (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  conversation_id BIGINT NOT NULL,
                  sender_id BIGINT NOT NULL,
                  msg_type VARCHAR(16) NOT NULL DEFAULT 'TEXT',
                  content TEXT NULL,
                  attachment_id BIGINT NULL,
                  reply_to_id BIGINT NULL,
                  recalled TINYINT NOT NULL DEFAULT 0,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_chat_msg_conv_id (conversation_id, id),
                  INDEX idx_chat_msg_sender (sender_id),
                  INDEX idx_chat_msg_reply (reply_to_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
        ensureColumn(c, "chat_message", "reply_to_id", "ALTER TABLE chat_message ADD COLUMN reply_to_id BIGINT NULL");
        ensureColumn(c, "chat_message", "recalled", "ALTER TABLE chat_message ADD COLUMN recalled TINYINT NOT NULL DEFAULT 0");
    }

    private void ensureAttachmentTable(Connection c) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS chat_attachment (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  uploader_id BIGINT NOT NULL,
                  original_name VARCHAR(255) NOT NULL,
                  content_type VARCHAR(128) NULL,
                  file_type VARCHAR(16) NOT NULL DEFAULT 'FILE',
                  size_bytes BIGINT NOT NULL DEFAULT 0,
                  storage_key VARCHAR(64) NOT NULL,
                  storage_path VARCHAR(512) NOT NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_chat_attach_uploader (uploader_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        try (Statement s = c.createStatement()) {
            s.execute(ddl);
        }
    }

    private void ensureUserProfileColumns(Connection c) {
        ensureColumn(c, "user", "avatar_url", "ALTER TABLE `user` ADD COLUMN avatar_url VARCHAR(512) NULL");
        ensureColumn(c, "user", "nickname", "ALTER TABLE `user` ADD COLUMN nickname VARCHAR(64) NULL");
        ensureColumn(c, "user", "signature", "ALTER TABLE `user` ADD COLUMN signature VARCHAR(255) NULL");
        ensureColumn(c, "user", "status", "ALTER TABLE `user` ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ONLINE'");
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
            System.err.println("CHAT_COLUMN_INIT_FAILED: " + table + "." + column + " " + e.getMessage());
        }
    }
}
