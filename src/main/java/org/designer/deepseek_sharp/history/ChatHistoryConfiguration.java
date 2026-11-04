package org.designer.deepseek_sharp.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 有 JdbcTemplate 且 MySQL 连得上就写库，否则退化成内存实现，保证 CLI 本身一定能用。
 */
@Configuration(proxyBeanMethods = false)
public class ChatHistoryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryConfiguration.class);

    @Bean
    ChatHistoryStore chatHistoryStore(ObjectProvider<JdbcTemplate> jdbcTemplates) {
        JdbcTemplate jdbc = jdbcTemplates.getIfAvailable();
        if (jdbc != null) {
            try {
                MySqlChatHistoryStore store = new MySqlChatHistoryStore(jdbc);
                store.initSchema();
                log.info("会话历史写入 MySQL（库 deepseek_sharp）");
                return store;
            } catch (RuntimeException ex) {
                log.warn("MySQL 不可用，本次历史只放内存：{}", rootMessage(ex));
            }
        } else {
            log.warn("没有可用的 DataSource，本次历史只放内存");
        }
        return new InMemoryChatHistoryStore();
    }

    private static String rootMessage(Throwable error) {
        String message = error.getMessage();
        Throwable cause = error.getCause();
        while (cause != null) {
            if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
                message = cause.getMessage();
            }
            cause = cause.getCause();
        }
        return message == null ? error.getClass().getSimpleName() : message.strip();
    }
}
