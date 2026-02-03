package com.bogda.common.model;

/**
 * AI 翻译结果封装，支持区分成功与失败类型（如 400 需直接走 Google）。
 */
public class AiTranslateResult {
    private final boolean success;
    private final String content;
    private final int tokenCount;
    /** 失败时的 HTTP 状态码，400 表示直接走 Google，其他表示可轮换下一模型 */
    private final Integer errorCode;

    private AiTranslateResult(boolean success, String content, int tokenCount, Integer errorCode) {
        this.success = success;
        this.content = content;
        this.tokenCount = tokenCount;
        this.errorCode = errorCode;
    }

    public static AiTranslateResult success(String content, int tokenCount) {
        return new AiTranslateResult(true, content, tokenCount, null);
    }

    /** @param errorCode 400 表示直接走 Google，其他表示可轮换下一模型 */
    public static AiTranslateResult fail(int errorCode) {
        return new AiTranslateResult(false, null, 0, errorCode);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getContent() {
        return content;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    /** 是否为 400 类错误，应直接走 Google 不再轮换 AI */
    public boolean isBadRequest() {
        return errorCode != null && errorCode == 400;
    }
}
