package com.aryn.cloud.upms.api.dto.ai;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;
public record AiConfigSaveCommand(long expectedRevision, List<ProviderCommand> providers, List<AiModel> models,
        Map<com.aryn.cloud.common.ai.contract.AiUsage,String> bindings) {
    public enum KeyOperation { KEEP, REPLACE, CLEAR }
    public record ProviderCommand(String id,String name,String protocol,String endpoint,boolean enabled,KeyOperation keyOperation,
            @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String apiKey) {
        @Override public String toString() { return "ProviderCommand[id="+id+",keyOperation="+keyOperation+"]"; }
    }
}
