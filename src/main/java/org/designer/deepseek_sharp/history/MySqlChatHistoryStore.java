package org.designer.deepseek_sharp.history;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** 用 JdbcTemplate 直接写 MySQL，表结构在 {@link #initSchema()} 里幂等创建。 */
public class MySqlChatHistoryStore implements ChatHistoryStore {

    private static final String DDL_SESSION = """
            CREATE TABLE IF NOT EXISTS chat_session (
                id         BIGINT       NOT NULL AUTO_INCREMENT,
                title      VARCHAR(120) NOT NULL,
                model      VARCHAR(64)  NOT NULL,
                created_at DATETIME(6)  NOT NULL,
                updated_at DATETIME(6)  NOT NULL,
                PRIMARY KEY (id)
            ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
            """;

    private static final String DDL_MESSAGE = """
            CREATE TABLE IF NOT EXISTS chat_message (
                id         BIGINT      NOT NULL AUTO_INCREMENT,
                session_id BIGINT      NOT NULL,
                role       VARCHAR(16) NOT NULL,
                content    MEDIUMTEXT  NOT NULL,
                created_at DATETIME(6) NOT NULL,
                PRIMARY KEY (id),
                KEY idx_chat_message_session (session_id, id),
                CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id)
                    REFERENCES chat_session (id) ON DELETE CASCADE
            ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
            """;

    private static final RowMapper<Session> SESSION_MAPPER = (rs, rowNum) -> new Session(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("model"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private static final RowMapper<StoredMessage> MESSAGE_MAPPER = (rs, rowNum) -> new StoredMessage(
            rs.getString("role"),
            rs.getString("content"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;

    public MySqlChatHistoryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void initSchema() {
        jdbc.execute(DDL_SESSION);
        jdbc.execute(DDL_MESSAGE);
    }

    @Override
    public Session createSession(String model, String title) {
        Timestamp now = Timestamp.from(Instant.now());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO chat_session (title, model, created_at, updated_at) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, title);
            ps.setString(2, model);
            ps.setTimestamp(3, now);
            ps.setTimestamp(4, now);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("MySQL 没有返回新会话的 id");
        }
        return new Session(key.longValue(), title, model, now.toInstant(), now.toInstant());
    }

    @Override
    public Optional<Session> findSession(long id) {
        List<Session> rows = jdbc.query(
                "SELECT id, title, model, created_at, updated_at FROM chat_session WHERE id = ?",
                SESSION_MAPPER, id);
        return rows.stream().findFirst();
    }

    @Override
    public List<Session> recentSessions(int limit) {
        return jdbc.query("""
                        SELECT id, title, model, created_at, updated_at
                        FROM chat_session
                        ORDER BY updated_at DESC, id DESC
                        LIMIT ?
                        """,
                SESSION_MAPPER, limit);
    }

    @Override
    public void renameSession(long id, String title) {
        jdbc.update("UPDATE chat_session SET title = ?, updated_at = ? WHERE id = ?",
                title, Timestamp.from(Instant.now()), id);
    }

    @Override
    public void appendMessage(long sessionId, String role, String content) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO chat_message (session_id, role, content, created_at) VALUES (?, ?, ?, ?)",
                sessionId, role, content, now);
        jdbc.update("UPDATE chat_session SET updated_at = ? WHERE id = ?", now, sessionId);
    }

    @Override
    public List<StoredMessage> recentMessages(long sessionId, int limit) {
        List<StoredMessage> rows = new ArrayList<>(jdbc.query("""
                        SELECT role, content, created_at
                        FROM chat_message
                        WHERE session_id = ?
                        ORDER BY id DESC
                        LIMIT ?
                        """,
                MESSAGE_MAPPER, sessionId, limit));
        Collections.reverse(rows);
        return rows;
    }

    @Override
    public long countMessages(long sessionId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM chat_message WHERE session_id = ?",
                Long.class, sessionId);
        return count == null ? 0L : count;
    }

    @Override
    public String describe() {
        return "MySQL · deepseek_sharp";
    }
}
