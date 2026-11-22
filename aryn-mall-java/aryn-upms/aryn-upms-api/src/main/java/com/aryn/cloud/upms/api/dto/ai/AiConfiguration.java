package com.aryn.cloud.upms.api.dto.ai;
import com.aryn.cloud.common.ai.contract.*;
import java.io.Serializable;
import java.util.*;
import java.time.Instant;
public record AiConfiguration(List<AiProvider> providers, List<AiModel> models, Map<AiUsage,String> bindings) implements Serializable {
    public AiConfiguration {
        providers=List.copyOf(providers); models=List.copyOf(models); bindings=Map.copyOf(bindings);
    }
}
