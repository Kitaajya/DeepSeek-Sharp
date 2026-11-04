package org.designer.deepseek_sharp.history;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** MySQL 不可用时的兜底：进程内保存，退出即丢。 */
public class InMemoryChatHistoryStore implements ChatHistoryStore {

    private final AtomicLong sessionIds = new AtomicLong();
    private final AtomicLong messageIds = new AtomicLong();
    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();
    private final Map<Long, List<StoredMessage>> messages = new ConcurrentHashMap<>();

    @Override
    public Session createSession(String model, String title) {
        long id = sessionIds.incrementAndGet();
        Instant now = Instant.now();
        Session session = new Session(id, title, model, now, now);
        sessions.put(id, session);
        messages.put(id, new ArrayList<>());
        return session;
    }

    @Override
    public Optional<Session> findSession(long id) {
        return Optional.ofNullable(sessions.get(id));
    }

    @Override
    public List<Session> recentSessions(int limit) {
        return sessions.values().stream()
                .sorted(Comparator.comparing(Session::updatedAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public void renameSession(long id, String title) {
        Session old = sessions.get(id);
        if (old != null) {
            sessions.put(id, new Session(old.id(), title, old.model(), old.createdAt(), Instant.now()));
        }
    }

    @Override
    public void appendMessage(long sessionId, String role, String content) {
        List<StoredMessage> list = messages.computeIfAbsent(sessionId, key -> new ArrayList<>());
        synchronized (list) {
            messageIds.incrementAndGet();
            list.add(new StoredMessage(role, content, Instant.now()));
        }
        Session old = sessions.get(sessionId);
        if (old != null) {
            sessions.put(sessionId, new Session(old.id(), old.title(), old.model(), old.createdAt(), Instant.now()));
        }
    }

    @Override
    public List<StoredMessage> recentMessages(long sessionId, int limit) {
        List<StoredMessage> list = messages.getOrDefault(sessionId, List.of());
        synchronized (list) {
            int from = Math.max(0, list.size() - Math.max(0, limit));
            return List.copyOf(list.subList(from, list.size()));
        }
    }

    @Override
    public long countMessages(long sessionId) {
        return messages.getOrDefault(sessionId, List.of()).size();
    }

    @Override
    public String describe() {
        return "内存（MySQL 不可用，重启即丢）";
    }
}
