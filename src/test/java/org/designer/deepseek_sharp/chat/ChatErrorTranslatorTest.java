package org.designer.deepseek_sharp.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatErrorTranslatorTest {

    @Test
    void mapsUnauthorizedToKeyHint() {
        String message = ChatErrorTranslator.translate(new RuntimeException("401 Unauthorized"));

        assertThat(message).contains("API Key").contains("401");
    }

    @Test
    void mapsBalanceAndRateLimit() {
        assertThat(ChatErrorTranslator.translate(new RuntimeException("402 Insufficient Balance")))
                .contains("余额不足");
        assertThat(ChatErrorTranslator.translate(new RuntimeException("429 Too Many Requests")))
                .contains("太频繁");
    }

    @Test
    void fallsBackToRootCause() {
        RuntimeException error = new RuntimeException("wrapper",
                new IllegalStateException("something odd happened"));

        assertThat(ChatErrorTranslator.translate(error)).contains("something odd happened");
    }
}
