package org.designer.deepseek_sharp;

import org.designer.deepseek_sharp.cli.CliOptions;
import org.designer.deepseek_sharp.cli.ConsoleUi;
import org.designer.deepseek_sharp.cli.PowerShellLauncher;
import org.designer.deepseek_sharp.cli.TerminalChat;
import org.designer.deepseek_sharp.key.ApiKeyResolver;
import org.designer.deepseek_sharp.key.ApiKeyStore;
import org.designer.deepseek_sharp.key.MissingApiKeyException;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * DeepSeek Harness —— 在终端里和 DeepSeek 聊天。
 *
 * <p>API Key 在 Spring 启动之前就要确定，因为 DeepSeekChatModel 这个 Bean 在创建时
 * 就要求 key 非空（否则 "DeepSeek API key must be set"），所以这里先做 key 解析，
 * 再把它塞进 Spring 的默认属性里。</p>
 */
@SpringBootApplication
public class DeepSeekSharpApplication {

    public static final String VERSION = "0.1.0";

    public static void main(String[] args) {
        CliOptions options = CliOptions.parse(args);
        ConsoleUi ui = new ConsoleUi(ConsoleUi.colorSupported(options.noColor()));

        if (options.help()) {
            printUsage(ui);
            return;
        }
        if (options.version()) {
            ui.line("DeepSeek Harness " + VERSION);
            return;
        }

        // IDEA 的 Run 窗口 / Gradle 管道 / 双击 jar 时没有真终端：
        // 自动开一个 Windows 自带的 PowerShell 窗口，把问答搬到那边去
        ApiKeyStore keyStore = new ApiKeyStore();
        if (PowerShellLauncher.shouldOpenWindow(options)) {
            try {
                String knownKey = ApiKeyResolver.peek(options.apiKey(), keyStore).orElse(null);
                PowerShellLauncher.open(options, args, knownKey);
                ui.hint("问答已在新开的 PowerShell 窗口里启动，这个窗口可以关掉了。");
                return;
            } catch (IOException ex) {
                ui.warn("自动打开 PowerShell 失败（" + ex.getMessage() + "），就在当前窗口继续。");
            }
        }

        // 真终端才用控制台自己的字符集（中文 Windows 可能是 GBK）；
        // 管道 / 重定向 / IDE 里按 UTF-8 读，避免把 UTF-8 的问题读成乱码
        Console console = System.console();
        Charset stdinCharset = (console != null && console.isTerminal())
                ? console.charset()
                : StandardCharsets.UTF_8;
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, stdinCharset));

        ApiKeyResolver.Resolved key;
        try {
            key = ApiKeyResolver.resolve(options.apiKey(), keyStore, ui, stdin);
        } catch (MissingApiKeyException ex) {
            ui.blank();
            ui.error(ex.getMessage());
            ui.hint("也可以：java -jar build/libs/deepseek-harness.jar --api-key sk-xxxx");
            ui.hint("或设置环境变量 DEEPSEEK_API_KEY，申请地址 https://platform.deepseek.com/api_keys");
            System.exit(2);
            return;
        } catch (IOException ex) {
            ui.error("读取 / 保存 API Key 失败：" + ex.getMessage());
            System.exit(2);
            return;
        }

        SpringApplication application = new SpringApplication(DeepSeekSharpApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setDefaultProperties(Map.of("spring.ai.deepseek.api-key", key.key()));

        ConfigurableApplicationContext context = application.run(args);
        int exitCode;
        try {
            exitCode = context.getBean(TerminalChat.class).start(options, stdin, ui, key);
        } finally {
            context.close();
        }
        System.exit(exitCode);
    }

    private static void printUsage(ConsoleUi ui) {
        ui.blank();
        ui.line(ui.bold("DeepSeek Harness " + VERSION)
                + " — 终端里的 DeepSeek 问答（Spring Boot + Spring AI + MySQL）");
        ui.blank();
        ui.line("用法:");
        ui.line("  java -jar build/libs/deepseek-harness.jar [选项]");
        ui.line("  ./gradlew bootRun --args=\"--ask 你好\"");
        ui.blank();
        ui.line("选项:");
        ui.line("  -k, --api-key <key>   指定 DeepSeek API Key（优先级最高）");
        ui.line("  -a, --ask <问题>      只问一次就退出，适合脚本 / 管道");
        ui.line("  -m, --model <模型>    deepseek-chat（默认）或 deepseek-reasoner");
        ui.line("  -s, --session <id>    继续某个历史会话（/sessions 里能看到 id）");
        ui.line("      --no-window       不自动弹 PowerShell，就在当前窗口里聊");
        ui.line("      --no-color        关闭 ANSI 颜色");
        ui.line("  -h, --help            显示本帮助");
        ui.line("  -v, --version         显示版本");
        ui.blank();
        ui.line("交互命令:");
        ui.line("  /help   /new   /sessions   /load <id>   /history   /model <名称>   /key   /exit");
        ui.blank();
        ui.hint("API Key 查找顺序：--api-key → 环境变量 DEEPSEEK_API_KEY → 本地配置文件 → 交互输入");
        ui.hint("本地配置文件：" + ApiKeyStore.defaultFile());
        ui.blank();
    }
}
