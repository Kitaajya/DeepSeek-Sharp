package org.designer.deepseek_sharp.key;

import org.designer.deepseek_sharp.cli.ConsoleUi;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * API Key 的查找顺序：
 * <ol>
 *   <li>{@code --api-key sk-xxx} 命令行参数</li>
 *   <li>{@code DEEPSEEK_API_KEY} 环境变量</li>
 *   <li>{@code ~/.deepseek-sharp/config.properties} 本地文件（上次输入过的）</li>
 *   <li>都没有就现场问用户要，然后存进第 3 步的文件</li>
 * </ol>
 */
public final class ApiKeyResolver {

    private ApiKeyResolver() {
    }

    public enum Source {
        ARGUMENT("命令行参数"),
        ENVIRONMENT("环境变量 DEEPSEEK_API_KEY"),
        CONFIG_FILE("本地配置文件"),
        PROMPT("刚刚输入");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Resolved(String key, Source source, Path configFile) {

        public String masked() {
            return mask(key);
        }
    }

    public static Resolved resolve(String fromArguments, ApiKeyStore store, ConsoleUi ui, BufferedReader stdin)
            throws IOException {
        if (usable(fromArguments)) {
            return new Resolved(fromArguments.trim(), Source.ARGUMENT, store.file());
        }
        String fromEnv = System.getenv("DEEPSEEK_API_KEY");
        if (usable(fromEnv)) {
            return new Resolved(fromEnv.trim(), Source.ENVIRONMENT, store.file());
        }
        Optional<String> saved = store.read();
        if (saved.isPresent()) {
            return new Resolved(saved.get(), Source.CONFIG_FILE, store.file());
        }

        String typed = prompt(ui, stdin);
        store.save(typed);
        ui.info("API Key 已保存到 " + store.file() + "，下次启动不用再输入。");
        return new Resolved(typed, Source.PROMPT, store.file());
    }

    /**
     * 只看不问了：参数 → 环境变量 → 配置文件。
     * 自动打开 PowerShell 窗口时用，避免在 IDE 控制台里先问一遍。
     */
    public static Optional<String> peek(String fromArguments, ApiKeyStore store) {
        if (usable(fromArguments)) {
            return Optional.of(fromArguments.trim());
        }
        String fromEnv = System.getenv("DEEPSEEK_API_KEY");
        if (usable(fromEnv)) {
            return Optional.of(fromEnv.trim());
        }
        return store.read();
    }

    public static String mask(String key) {
        if (key == null || key.isBlank()) {
            return "****";
        }
        String trimmed = key.trim();
        if (trimmed.length() <= 12) {
            return "****";
        }
        return trimmed.substring(0, 6) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private static boolean usable(String key) {
        return key != null && !key.isBlank();
    }

    private static String prompt(ConsoleUi ui, BufferedReader stdin) throws IOException {
        Console console = System.console();
        ui.blank();
        ui.warn("还没有配置 DeepSeek API Key。");
        ui.hint("  申请地址：https://platform.deepseek.com/api_keys");
        ui.hint("  Key 只保存在本机 " + ApiKeyStore.defaultFile() + "，不会进 git。");
        ui.blank();

        while (true) {
            String value;
            if (console != null) {
                char[] typed = console.readPassword("粘贴 API Key（不回显，回车确认）: ");
                value = typed == null ? null : new String(typed);
            } else {
                ui.print("粘贴 API Key（当前环境会回显）: ");
                value = stdin.readLine();
            }
            if (value == null) {
                throw new MissingApiKeyException("没有输入 API Key，已取消。");
            }
            value = value.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (!value.startsWith("sk-")) {
                ui.warn("DeepSeek 的 Key 通常以 sk- 开头；先按你输入的用，报 401 时再用 /key 改。");
            }
            return value;
        }
    }
}
