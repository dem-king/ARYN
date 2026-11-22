package com.aryn.cloud.common.ai.error;
/** 不保留供应商原文/原始异常，避免凭证和客户输入随异常进入日志。 */
public final class AiCallException extends RuntimeException {
    private final AiErrorCode code;
    public AiCallException(AiErrorCode code) { super("AI 调用失败：" + code); this.code = code; }
    public AiErrorCode code() { return code; }
}
