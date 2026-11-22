package com.aryn.cloud.upms.api.dto.ai;
import com.aryn.cloud.common.ai.contract.*;
import java.io.Serializable;
import java.util.*;
import java.time.Instant;
public record AiProvider(String id, String name, String protocol, String endpoint, String credentialVersion, boolean enabled) implements Serializable {}
