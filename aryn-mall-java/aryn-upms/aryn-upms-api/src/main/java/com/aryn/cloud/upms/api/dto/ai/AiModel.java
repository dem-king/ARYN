package com.aryn.cloud.upms.api.dto.ai;
import com.aryn.cloud.common.ai.contract.*;
import java.io.Serializable;
import java.util.*;
import java.time.Instant;
public record AiModel(String id, String providerId, String model, AiCapability capability, int timeoutMillis, int maxInputChars, int maxOutputTokens, Integer dimension, Map<String,Object> parameters) implements Serializable {}
