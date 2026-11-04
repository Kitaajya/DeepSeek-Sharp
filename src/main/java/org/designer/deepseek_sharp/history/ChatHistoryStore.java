package org.designer.deepseek_sharp.history;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 会话历史存储。默认写 MySQL；MySQL 不可用时自动退化成内存实现，保证终端问答本身仍能跑。
 */
public interface ChatHistoryStore {

    record Session(long id, String title, String model, Instant createdAt, Instant updatedAt) {
    }

    record StoredMessage(String role, String content, Instant createdAt) {
    }

    Session createSession(String model, String title);

    Optional<Session> findSession(long id);

    List<Session> recentSessions(int limit);

    void renameSession(long id, String title);

    void appendMessage(long sessionId, String role, String content);

    /** 按时间正序返回最近 limit 条消息，用于拼上下文。 */
    List<StoredMessage> recentMessages(long sessionId, int limit);

    long countMessages(long sessionId);

    /** 给界面显示的一句话描述，比如 "MySQL · deepseek_sharp"。 */
    String describe();
}
