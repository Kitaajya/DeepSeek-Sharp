package org.designer.deepseek_sharp.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** ChatClient 全局只建一次，系统提示词可以通过 deepseek.system-prompt 覆盖。 */
@Configuration(proxyBeanMethods = false)
public class ChatClientConfiguration {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是 DeepSeek Harness 的终端助手，运行在用户的命令行里。
            用简体中文回答，条理清晰、先给结论再给细节；代码放进 Markdown 代码块。
            不知道就直说，不要编造。""";

    @Bean
    ChatClient chatClient(ChatModel chatModel,
                          @Value("${deepseek.system-prompt:}") String systemPrompt) {
        String prompt = (systemPrompt == null || systemPrompt.isBlank()) ? DEFAULT_SYSTEM_PROMPT : systemPrompt;
        return ChatClient.builder(chatModel).defaultSystem(prompt).build();
    }
}
