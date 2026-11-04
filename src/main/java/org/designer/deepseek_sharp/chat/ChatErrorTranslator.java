package org.designer.deepseek_sharp.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 把 Spring AI / RestClient / 网络层的异常翻译成人话，顺便给出下一步怎么办。 */
public final class ChatErrorTranslator {

    private ChatErrorTranslator() {
    }

    public static String translate(Throwable error) {
        String text = collect(error).toLowerCase(Locale.ROOT);

        if (containsAny(text, "401", "unauthorized", "invalid api key", "invalid_api_key",
                "authentication", "api key must be set")) {
            return "API Key 无效或已失效（401）。用 /key 重新填写，或检查环境变量 DEEPSEEK_API_KEY。";
        }
        if (containsAny(text, "402", "insufficient balance", "insufficient_quota")) {
            return "DeepSeek 账户余额不足（402），充值后再试。";
        }
        if (containsAny(text, "429", "rate limit", "too many requests", "rate_limit")) {
            return "请求太频繁（429），等几秒再试。";
        }
        if (containsAny(text, "timeout", "timed out", "readtimeout")) {
            return "请求超时了，检查网络 / 代理后重试。";
        }
        if (containsAny(text, "unknownhost", "unknown host", "connection refused", "connect timed out",
                "network is unreachable", "nodename nor servname", "failed to connect")) {
            return "连不上 api.deepseek.com，检查网络或代理设置。";
        }
        if (containsAny(text, "403", "forbidden")) {
            return "没有访问权限（403），确认这个 Key 是否被禁用。";
        }
        if (containsAny(text, "404")) {
            return "接口地址不对（404），检查 spring.ai.deepseek.base-url / completions-path。";
        }
        if (containsAny(text, "400", "bad request")) {
            return "请求被拒绝了（400）：" + rootMessage(error);
        }
        if (containsAny(text, "500", "502", "503", "504", "service unavailable")) {
            return "DeepSeek 服务端暂时异常，稍后再试。";
        }
        return "调用失败：" + rootMessage(error);
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String collect(Throwable error) {
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        int guard = 0;
        while (current != null && guard++ < 20) {
            if (current.getMessage() != null) {
                builder.append(current.getMessage()).append('\n');
            }
            builder.append(current.getClass().getName()).append('\n');
            current = current.getCause();
        }
        return builder.toString();
    }

    public static String rootMessage(Throwable error) {
        List<String> messages = new ArrayList<>();
        Throwable current = error;
        int guard = 0;
        while (current != null && guard++ < 20) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                messages.add(current.getMessage().strip());
            }
            current = current.getCause();
        }
        if (messages.isEmpty()) {
            return error.getClass().getSimpleName();
        }
        String deepest = messages.get(messages.size() - 1);
        if (deepest.length() > 300) {
            deepest = deepest.substring(0, 300) + "…";
        }
        return deepest;
    }
}
