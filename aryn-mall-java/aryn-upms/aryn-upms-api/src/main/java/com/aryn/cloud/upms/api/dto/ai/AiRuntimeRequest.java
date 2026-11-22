package com.aryn.cloud.upms.api.dto.ai;
import com.aryn.cloud.common.ai.contract.*;
import java.io.Serializable;
import java.util.*;
import java.time.Instant;
public record AiRuntimeRequest(AiCallContext context, String caller, long issuedAt, String signature, String version, String providerId, long emergencyEpoch) implements Serializable {
    @Override public String toString() { return "AiRuntimeRequest[caller="+caller+",version="+version+"]"; }
}
