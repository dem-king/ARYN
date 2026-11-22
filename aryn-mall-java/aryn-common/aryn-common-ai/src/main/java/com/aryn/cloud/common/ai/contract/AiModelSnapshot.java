package com.aryn.cloud.common.ai.contract;
import java.io.Serializable;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
/** 任务启动时固定，凭证替换创建新版本；禁止把此类型作为管理接口响应。 */
public record AiModelSnapshot(String configVersion, String credentialVersion, String providerId,
        URI endpoint, String model, AiCapability capability, int timeoutMillis, int maxInputChars,
        int maxOutputTokens, Integer dimension, Map<String,Object> parameters, AiSecret credential) implements Serializable {
    public AiModelSnapshot {
        if (configVersion == null || credentialVersion == null || providerId == null || model == null || model.isBlank()) throw new IllegalArgumentException("AI 配置不完整");
        Objects.requireNonNull(endpoint); Objects.requireNonNull(credential);
        if (capability != AiCapability.STRUCTURED_TEXT && capability != AiCapability.EMBEDDING) throw new IllegalArgumentException("尚未适配该能力");
        if (timeoutMillis < 1 || timeoutMillis > 120000 || maxInputChars < 1 || maxInputChars > 1000000 || maxOutputTokens < 1 || maxOutputTokens > 32768) throw new IllegalArgumentException("AI 调用限制无效");
        if (capability == AiCapability.EMBEDDING && (dimension == null || dimension < 1 || dimension > 16384)) throw new IllegalArgumentException("向量维度无效");
        parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
        Set<String> allowed = capability == AiCapability.EMBEDDING ? Set.of("dimensions") : Set.of("temperature", "top_p", "thinking", "response_format");
        for (var entry : parameters.entrySet()) {
            String key = entry.getKey(); Object value = entry.getValue();
            if (!allowed.contains(key)) throw new IllegalArgumentException("未适配的模型参数");
            if (key.equals("dimensions") && (!(value instanceof Number n) || n.doubleValue() != dimension)) throw new IllegalArgumentException("请求维度必须匹配索引维度");
            if (key.equals("temperature") || key.equals("top_p")) {
                double upper = key.equals("temperature") ? 2 : 1;
                if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() < 0 || n.doubleValue() > upper) throw new IllegalArgumentException("采样参数无效");
            }
            if (key.equals("thinking") && !(value instanceof Boolean)) throw new IllegalArgumentException("thinking 必须是布尔值");
            if (key.equals("response_format") && !Set.of("json_object").contains(value)) throw new IllegalArgumentException("结构化输出参数无效");
        }
    }
}
