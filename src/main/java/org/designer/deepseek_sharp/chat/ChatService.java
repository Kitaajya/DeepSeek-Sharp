package org.designer.deepseek_sharp.chat;

import org.designer.deepseek_sharp.history.ChatHistoryStore;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 一次问答：拉最近的历史拼上下文 → 流式调 DeepSeek → 落库。 */
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ChatHistoryStore history;
    private final int historyLimit;

    public ChatService(ChatClient chatClient,
                       ChatHistoryStore history,
                       @Value("${deepseek.history-limit:20}") int historyLimit) {
        this.chatClient = chatClient;
        this.history = history;
        this.historyLimit = historyLimit;
    }

    /**
     * @param onToken 每收到一个流式片段就回调一次，负责往终端上打
     * @return 完整回答
     */
    public String ask(long sessionId, String model, String question, Consumer<String> onToken) {
        List<Message> context = new ArrayList<>();
        for (ChatHistoryStore.StoredMessage stored : history.recentMessages(sessionId, historyLimit)) {
            context.add("user".equalsIgnoreCase(stored.role())
                    ? new UserMessage(stored.content())
                    : new AssistantMessage(stored.content()));
        }

        history.appendMessage(sessionId, "user", question);

        StringBuilder answer = new StringBuilder();
        try {
            chatClient.prompt()
                    .messages(context)
                    .user(question)
                    .options(DeepSeekChatOptions.builder().model(model))
                    .stream()
                    .content()
                    .toIterable()
                    .forEach(token -> {
                        answer.append(token);
                        onToken.accept(token);
                    });
        } catch (RuntimeException ex) {
            throw new ChatException(ChatErrorTranslator.translate(ex), ex);
        }

        String text = answer.toString();
        if (text.isBlank()) {
            throw new ChatException("DeepSeek 返回了空内容，可能是网络抖动，重试一下。");
        }
        history.appendMessage(sessionId, "assistant", text);
        return text;
    }
}
