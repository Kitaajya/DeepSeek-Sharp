package org.designer.deepseek_sharp.cli;

/**
 * 本程序自己的命令行参数。Spring 的参数（--server.port 之类）不在这里处理，
 * 解析不了的一律忽略并继续交给 SpringApplication。
 */
public record CliOptions(
        String apiKey,
        String ask,
        String model,
        Long sessionId,
        boolean noColor,
        boolean noWindow,
        boolean inWindow,
        boolean help,
        boolean version) {

    public static CliOptions parse(String[] args) {
        String apiKey = null;
        String ask = null;
        String model = null;
        Long sessionId = null;
        boolean noColor = false;
        boolean noWindow = false;
        boolean inWindow = false;
        boolean help = false;
        boolean version = false;

        for (int i = 0; i < args.length; i++) {
            String raw = args[i];
            if (!raw.startsWith("-")) {
                continue;
            }
            int eq = raw.indexOf('=');
            String name = eq > 0 ? raw.substring(0, eq) : raw;
            String inline = eq > 0 ? raw.substring(eq + 1) : null;

            switch (name) {
                case "--api-key", "-k" -> apiKey = inline != null ? inline : (i + 1 < args.length ? args[++i] : null);
                case "--ask", "-a" -> ask = inline != null ? inline : (i + 1 < args.length ? args[++i] : null);
                case "--model", "-m" -> model = inline != null ? inline : (i + 1 < args.length ? args[++i] : null);
                case "--session", "-s" -> sessionId = parseSession(inline != null ? inline : (i + 1 < args.length ? args[++i] : null));
                case "--no-color" -> noColor = true;
                case "--no-window" -> noWindow = true;
                case "--in-window" -> inWindow = true;
                case "--help", "-h" -> help = true;
                case "--version", "-v" -> version = true;
                default -> {
                    // 其它参数（如 --spring.profiles.active=...）留给 Spring
                }
            }
        }
        return new CliOptions(apiKey, ask, model, sessionId, noColor, noWindow, inWindow, help, version);
    }

    private static Long parseSession(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
