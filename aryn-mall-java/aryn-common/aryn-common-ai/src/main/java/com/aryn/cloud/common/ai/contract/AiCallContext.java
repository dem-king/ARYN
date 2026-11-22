package com.aryn.cloud.common.ai.contract;
import java.io.Serializable;
import java.util.Objects;
public record AiCallContext(String callId, String taskId, String tenantId, AiUsage usage) implements Serializable {
    public AiCallContext {
        if (callId == null || callId.isBlank() || taskId == null || taskId.isBlank() || tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("AI 调用上下文不完整");
        Objects.requireNonNull(usage);
    }
}
