package org.designer.deepseek_sharp.cli;

import org.designer.deepseek_sharp.DeepSeekSharpApplication;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

/**
 * 当程序不是跑在真正的终端里（IDEA 的 Run 窗口、Gradle 管道、双击 jar）时，
 * 自动用 Windows 自带的 PowerShell 开一个新窗口，把问答放到那个窗口里跑。
 *
 * <p>子进程用环境变量 {@value #GUARD_ENV} 和参数 --in-window 双重标记，
 * 保证不会再递归开窗口。</p>
 */
public final class PowerShellLauncher {

    private static final String GUARD_ENV = "DEEPSEEK_HARNESS_IN_WINDOW";
    private static final String SCRIPT_NAME = "deepseek-harness.ps1";

    private PowerShellLauncher() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** 是否应该弹窗：Windows + 交互模式 + 当前不是真终端 + 没被禁止 + 不是子进程。 */
    public static boolean shouldOpenWindow(CliOptions options) {
        if (!isWindows()) {
            return false;
        }
        if (options.noWindow() || options.inWindow() || options.ask() != null) {
            return false;
        }
        if ("1".equals(System.getenv(GUARD_ENV))) {
            return false;
        }
        Console console = System.console();
        return console == null || !console.isTerminal();
    }

    /**
     * 生成一个临时 .ps1 并让 cmd 的 start 打开新窗口执行它。
     *
     * @param apiKey 已经找到的 Key；为 null 时子进程会自己在窗口里问用户要
     */
    public static void open(CliOptions options, String[] rawArgs, String apiKey) throws IOException {
        Path script = writeScript(rawArgs);

        List<String> command = new ArrayList<>();
        command.add("cmd.exe");
        command.add("/c");
        command.add("start");
        command.add("");                       // start 的第一个引号参数是窗口标题
        command.add("powershell.exe");
        command.add("-NoLogo");
        command.add("-NoProfile");
        command.add("-ExecutionPolicy");
        command.add("Bypass");
        command.add("-File");
        command.add(script.toString());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(new File(System.getProperty("user.dir")));
        builder.environment().put(GUARD_ENV, "1");
        if (apiKey != null && !apiKey.isBlank()) {
            // 用环境变量传给子进程，避免 Key 出现在窗口标题、脚本文件或进程参数里
            builder.environment().put("DEEPSEEK_API_KEY", apiKey);
        }
        builder.start();
    }

    public static Path scriptPath() {
        return Path.of(System.getProperty("java.io.tmpdir"), SCRIPT_NAME);
    }

    private static Path writeScript(String[] rawArgs) throws IOException {
        StringBuilder script = new StringBuilder();
        script.append("$ErrorActionPreference = 'Continue'\r\n");
        script.append("try { $Host.UI.RawUI.WindowTitle = 'DeepSeek Harness' } catch { }\r\n");
        script.append("Set-Location -LiteralPath '").append(quote(System.getProperty("user.dir"))).append("'\r\n");

        script.append("& '").append(quote(javaExecutable())).append("'");
        for (String arg : javaArguments(rawArgs)) {
            script.append(" '").append(quote(arg)).append("'");
        }
        script.append("\r\n");

        script.append("if ($LASTEXITCODE -ne 0) {\r\n");
        script.append("  Write-Host ''\r\n");
        script.append("  Write-Host \"程序退出码 $LASTEXITCODE，按回车关闭窗口\" -ForegroundColor Yellow\r\n");
        script.append("  [void](Read-Host)\r\n");
        script.append("}\r\n");

        Path scriptPath = scriptPath();
        Files.writeString(scriptPath, script.toString(), StandardCharsets.UTF_8);
        return scriptPath;
    }

    private static List<String> javaArguments(String[] rawArgs) {
        List<String> args = new ArrayList<>();
        int feature = Runtime.version().feature();
        if (feature >= 24) {
            args.add("--enable-native-access=ALL-UNNAMED");
            args.add("--sun-misc-unsafe-memory-access=allow");
        }

        String fatJar = fatJarPath();
        if (fatJar != null) {
            args.add("-jar");
            args.add(fatJar);
        } else {
            args.add("-cp");
            args.add(System.getProperty("java.class.path"));
            args.add(DeepSeekSharpApplication.class.getName());
        }

        args.add("--in-window");
        for (String arg : stripSensitive(rawArgs)) {
            args.add(arg);
        }
        return args;
    }

    /** 把 --api-key 摘掉：Key 已经通过环境变量传过去，不留在脚本文件里。 */
    private static List<String> stripSensitive(String[] rawArgs) {
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < rawArgs.length; i++) {
            String arg = rawArgs[i];
            if ("--api-key".equals(arg) || "-k".equals(arg)) {
                i++;
                continue;
            }
            if (arg.startsWith("--api-key=")) {
                continue;
            }
            kept.add(arg);
        }
        return kept;
    }

    private static String javaExecutable() {
        return ProcessHandle.current().info().command()
                .orElseGet(() -> Path.of(System.getProperty("java.home"), "bin", "java.exe").toString());
    }

    /** 只有可执行 jar（里面有 BOOT-INF/）才用 -jar；IDE / bootRun 的 classpath 走 -cp。 */
    private static String fatJarPath() {
        String classpath = System.getProperty("java.class.path");
        if (classpath == null) {
            return null;
        }
        String[] entries = classpath.split(File.pathSeparator);
        if (entries.length != 1) {
            return null;
        }
        Path path = Path.of(entries[0]);
        if (!path.toString().toLowerCase(Locale.ROOT).endsWith(".jar") || !Files.isRegularFile(path)) {
            return null;
        }
        try (var zip = new ZipFile(path.toFile())) {
            boolean bootJar = zip.getEntry("BOOT-INF/") != null || zip.getEntry("BOOT-INF/classes/") != null;
            return bootJar ? path.toAbsolutePath().toString() : null;
        } catch (IOException ex) {
            return null;
        }
    }

    private static String quote(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}
