package org.designer.deepseek_sharp.cli;

import org.designer.deepseek_sharp.DeepSeekSharpApplication;
import org.designer.deepseek_sharp.chat.ChatException;
import org.designer.deepseek_sharp.chat.ChatService;
import org.designer.deepseek_sharp.history.ChatHistoryStore;
import org.designer.deepseek_sharp.key.ApiKeyResolver;
import org.designer.deepseek_sharp.key.ApiKeyStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** 终端里那个交互循环：读一行 → 判断是命令还是问题 → 流式打印回答。 */
@Component
public class TerminalChat {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final int TITLE_LIMIT = 40;

    private final ChatService chatService;
    private final ChatHistoryStore history;
    private final String defaultModel;

    public TerminalChat(ChatService chatService,
                        ChatHistoryStore history,
                        @Value("${spring.ai.deepseek.chat.options.model:deepseek-chat}") String defaultModel) {
        this.chatService = chatService;
        this.history = history;
        this.defaultModel = defaultModel;
    }

    public int start(CliOptions options, BufferedReader stdin, ConsoleUi ui, ApiKeyResolver.Resolved key) {
        State state = new State();
        state.model = (options.model() == null || options.model().isBlank())
                ? defaultModel
                : options.model().trim();

        if (options.sessionId() != null) {
            Optional<ChatHistoryStore.Session> existing = history.findSession(options.sessionId());
            if (existing.isPresent()) {
                state.session = existing.get();
                state.model = state.session.model();
            } else {
                ui.warn("找不到会话 #" + options.sessionId() + "，这次会开新的。");
            }
        }

        if (options.ask() != null) {
            return askOnce(state, options.ask(), ui);
        }

        String sessionLabel = state.session == null ? "（还没开始）" : "#" + state.session.id();
        ui.banner(DeepSeekSharpApplication.VERSION, state.model, sessionLabel, history.describe());
        ui.hint("  API Key " + key.masked() + "   （来源：" + key.source().label() + "）");
        ui.blank();
        return repl(state, stdin, ui);
    }

    private int repl(State state, BufferedReader stdin, ConsoleUi ui) {
        while (true) {
            ui.print(ui.userPrompt() + " ");
            String line;
            try {
                line = stdin.readLine();
            } catch (IOException ex) {
                ui.error("读取输入失败：" + ex.getMessage());
                return 1;
            }
            if (line == null) {
                ui.blank();
                ui.hint("输入结束，再见 👋");
                return 0;
            }
            line = line.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("/")) {
                if (!handleCommand(line, state, stdin, ui)) {
                    return 0;
                }
                continue;
            }
            answer(state, line, ui);
        }
    }

    private void answer(State state, String question, ConsoleUi ui) {
        ChatHistoryStore.Session session = sessionFor(state, question);
        ui.print(ui.aiPrompt() + " ");
        try {
            chatService.ask(session.id(), state.model, question, ui::raw);
            ui.blank();
        } catch (ChatException ex) {
            ui.blank();
            ui.error(ex.getMessage());
        }
        ui.blank();
    }

    /** 第一句问话时才真正建会话，避免每启动一次就在库里留一条空记录。 */
    private ChatHistoryStore.Session sessionFor(State state, String question) {
        if (state.session == null) {
            state.session = history.createSession(state.model, shorten(question));
            return state.session;
        }
        if (history.countMessages(state.session.id()) == 0) {
            state.session = rename(state.session, shorten(question));
        }
        return state.session;
    }

    private boolean handleCommand(String line, State state, BufferedReader stdin, ConsoleUi ui) {
        String[] parts = line.split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        String argument = parts.length > 1 ? parts[1].strip() : "";

        switch (command) {
            case "/help", "/?" -> printHelp(ui);
            case "/new" -> {
                state.session = null;
                ui.info("下一条消息会开一个新会话（模型 " + state.model + "）。");
            }
            case "/sessions" -> listSessions(ui);
            case "/load" -> loadSession(argument, state, ui);
            case "/history" -> printHistory(state, ui);
            case "/model" -> switchModel(argument, state, ui);
            case "/key" -> changeKey(stdin, ui);
            case "/exit", "/quit", "/q" -> {
                ui.hint("再见 👋");
                return false;
            }
            default -> ui.warn("没有 " + command + " 这个命令，输入 /help 看看。");
        }
        ui.blank();
        return true;
    }

    private int askOnce(State state, String question, ConsoleUi ui) {
        try {
            ChatHistoryStore.Session session = sessionFor(state, question);
            chatService.ask(session.id(), state.model, question, ui::raw);
            ui.blank();
            return 0;
        } catch (ChatException ex) {
            ui.error(ex.getMessage());
            return 1;
        }
    }

    private void printHelp(ConsoleUi ui) {
        ui.line(ui.bold("命令："));
        ui.line("  /new              开一个新会话");
        ui.line("  /sessions         列出最近的会话（带 id）");
        ui.line("  /load <id>        接着某个会话聊");
        ui.line("  /history          看当前会话最近 10 条消息");
        ui.line("  /model <名称>     deepseek-chat / deepseek-reasoner");
        ui.line("  /key              换 API Key（保存后需重启生效）");
        ui.line("  /exit             退出（Ctrl+C 也行）");
    }

    private void listSessions(ConsoleUi ui) {
        List<ChatHistoryStore.Session> sessions = history.recentSessions(10);
        if (sessions.isEmpty()) {
            ui.hint("还没有历史会话。");
            return;
        }
        ui.line(ui.bold("最近的会话："));
        for (ChatHistoryStore.Session session : sessions) {
            ui.line(String.format("  #%-4d %-9s %s  %s",
                    session.id(), session.model(), TIME_FORMAT.format(session.updatedAt()), session.title()));
        }
        ui.hint("  想接着聊：/load <id>");
    }

    private void loadSession(String argument, State state, ConsoleUi ui) {
        if (argument.isBlank()) {
            ui.warn("用法：/load <id>，id 从 /sessions 里看。");
            return;
        }
        long id;
        try {
            id = Long.parseLong(argument);
        } catch (NumberFormatException ex) {
            ui.warn("id 得是数字，例如 /load 12");
            return;
        }
        Optional<ChatHistoryStore.Session> session = history.findSession(id);
        if (session.isEmpty()) {
            ui.warn("找不到会话 #" + id);
            return;
        }
        state.session = session.get();
        state.model = state.session.model();
        ui.info("已切到会话 #" + id + "（" + history.countMessages(id) + " 条历史，模型 " + state.model + "）");
    }

    private void printHistory(State state, ConsoleUi ui) {
        if (state.session == null) {
            ui.hint("还没开始聊，先问个问题吧。");
            return;
        }
        List<ChatHistoryStore.StoredMessage> messages = history.recentMessages(state.session.id(), 10);
        if (messages.isEmpty()) {
            ui.hint("本会话还没有消息。");
            return;
        }
        ui.line(ui.bold("最近 10 条："));
        for (ChatHistoryStore.StoredMessage message : messages) {
            String who = "user".equalsIgnoreCase(message.role()) ? ui.cyan("你 ") : ui.green("AI ");
            ui.line("  " + who + " " + oneLine(message.content(), 160));
        }
    }

    private void switchModel(String argument, State state, ConsoleUi ui) {
        if (argument.isBlank()) {
            ui.info("当前模型 " + state.model + "；可用：deepseek-chat / deepseek-reasoner");
            return;
        }
        state.model = argument;
        ui.info("已切到 " + state.model + "（当前进程生效）");
    }

    private void changeKey(BufferedReader stdin, ConsoleUi ui) {
        ui.hint("当前 Key 保存的位置：" + ApiKeyStore.defaultFile());
        Console console = System.console();
        try {
            String typed;
            if (console != null) {
                char[] chars = console.readPassword("新的 API Key（不回显）: ");
                typed = chars == null ? null : new String(chars);
            } else {
                ui.print("新的 API Key（会回显）: ");
                typed = stdin.readLine();
            }
            if (typed == null || typed.isBlank()) {
                ui.warn("没有输入，保持原来的 Key。");
                return;
            }
            new ApiKeyStore().save(typed.trim());
            ui.info("已保存。DeepSeek 客户端在启动时创建，重启程序后新 Key 才生效。");
        } catch (IOException ex) {
            ui.error("保存失败：" + ex.getMessage());
        }
    }

    private ChatHistoryStore.Session rename(ChatHistoryStore.Session session, String title) {
        history.renameSession(session.id(), title);
        return new ChatHistoryStore.Session(session.id(), title, session.model(),
                session.createdAt(), session.updatedAt());
    }

    private static String shorten(String text) {
        String oneLine = text.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= TITLE_LIMIT ? oneLine : oneLine.substring(0, TITLE_LIMIT) + "…";
    }

    private static String oneLine(String text, int limit) {
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= limit ? flat : flat.substring(0, limit) + "…";
    }

    /** 当前会话 / 当前模型，命令会改它们。session 为 null 表示"还没开始聊"。 */
    private static final class State {
        private ChatHistoryStore.Session session;
        private String model;
    }
}
