package org.designer.deepseek_sharp.cli;

import java.io.Console;
import java.io.PrintStream;

/**
 * 终端输出的小工具：统一着色、统一前缀，并且在没有真 console（IDE、管道、重定向）时自动关掉 ANSI。
 */
public final class ConsoleUi {

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[90m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";

    private final boolean color;
    private final PrintStream out;

    public ConsoleUi(boolean color) {
        this(color, System.out);
    }

    public ConsoleUi(boolean color, PrintStream out) {
        this.color = color;
        this.out = out;
    }

    public static boolean colorSupported(boolean disabledByFlag) {
        if (disabledByFlag) {
            return false;
        }
        if (System.getenv("NO_COLOR") != null) {
            return false;
        }
        if ("dumb".equalsIgnoreCase(System.getenv("TERM"))) {
            return false;
        }
        // JDK 22 起 System.console() 在重定向时也不为 null，要看 isTerminal()：
        // IDE 运行窗口 / 管道 / 重定向到文件时都不该输出 ANSI 转义
        Console console = System.console();
        return console != null && console.isTerminal();
    }

    public void line(String text) {
        out.println(text);
    }

    public void blank() {
        out.println();
    }

    public void print(String text) {
        out.print(text);
        out.flush();
    }

    /** 流式输出用：不换行，立刻刷出去 */
    public void raw(String chunk) {
        out.print(chunk);
        out.flush();
    }

    public void info(String message) {
        out.println(cyan("· " + message));
        out.flush();
    }

    public void warn(String message) {
        out.println(yellow("! " + message));
        out.flush();
    }

    public void error(String message) {
        out.println(red("✗ " + message));
        out.flush();
    }

    public void hint(String message) {
        out.println(dim(message));
        out.flush();
    }

    public String userPrompt() {
        return bold(cyan("你 ›"));
    }

    public String aiPrompt() {
        return bold(green("AI ›"));
    }

    public String bold(String text) {
        return color ? BOLD + text + RESET : text;
    }

    public String dim(String text) {
        return color ? DIM + text + RESET : text;
    }

    public String cyan(String text) {
        return color ? CYAN + text + RESET : text;
    }

    public String green(String text) {
        return color ? GREEN + text + RESET : text;
    }

    public String yellow(String text) {
        return color ? YELLOW + text + RESET : text;
    }

    public String red(String text) {
        return color ? RED + text + RESET : text;
    }

    public void banner(String version, String model, String sessionLabel, String historyStore) {
        String rule = "─".repeat(56);
        line(dim(rule));
        line("  " + bold("DeepSeek Harness " + version) + dim("  ·  终端问答"));
        line(dim("  模型 ") + model + dim("    会话 ") + sessionLabel);
        line(dim("  历史 ") + historyStore);
        line(dim(rule));
        hint("  输入问题回车发送；/help 看命令；/exit 退出。");
        blank();
    }
}
