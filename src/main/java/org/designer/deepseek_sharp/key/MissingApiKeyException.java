package org.designer.deepseek_sharp.key;

/** 用户既没给 Key 也不愿意输入时抛出，main 里会转成退出码 2。 */
public class MissingApiKeyException extends RuntimeException {

    public MissingApiKeyException(String message) {
        super(message);
    }
}
