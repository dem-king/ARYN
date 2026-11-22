package com.aryn.cloud.upms.api.dto.ai;
import com.aryn.cloud.common.ai.contract.*;
import java.io.Serializable;
import java.util.*;
import java.time.Instant;
public record AiConfigTestResult(String version, String modelId, boolean passed, String status, Instant testedAt) implements Serializable {}
