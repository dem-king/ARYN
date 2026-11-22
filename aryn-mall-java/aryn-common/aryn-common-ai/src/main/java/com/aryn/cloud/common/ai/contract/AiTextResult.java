package com.aryn.cloud.common.ai.contract;
import com.fasterxml.jackson.databind.JsonNode;
public record AiTextResult(JsonNode output, Long totalTokens, String configVersion, long elapsedMillis) { }
