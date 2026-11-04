package org.designer.deepseek_sharp.chat;

/** 聊天过程中的可展示错误：message 已经翻译成给用户看的中文。 */
public class ChatException extends RuntimeException {

    public ChatException(String message) {
        super(message);
    }

    public ChatException(String message, Throwable cause) {
        super(message, cause);
    }
}
