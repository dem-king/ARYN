package com.aryn.cloud.upms.api.dto.ai;
import com.aryn.cloud.common.ai.contract.*;
import java.io.Serializable;
import java.util.*;
import java.time.Instant;
public record AiConfigView(String version, long revision, String activeVersion, long emergencyEpoch, boolean disabled, Set<String> disabledProviders, AiConfiguration configuration) implements Serializable {}
