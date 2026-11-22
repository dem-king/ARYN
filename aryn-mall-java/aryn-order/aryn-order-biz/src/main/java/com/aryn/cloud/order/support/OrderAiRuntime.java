package com.aryn.cloud.order.support;
import com.aryn.cloud.upms.api.client.AiRuntimeClient;
import com.aryn.cloud.upms.api.remote.RemoteAiConfigService;
import com.aryn.cloud.common.ai.contract.AiUsage;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class OrderAiRuntime {
    @DubboReference(check=false) private RemoteAiConfigService remote;
    private final String token; private final Set<String> allowedHosts;
    public OrderAiRuntime(@Value("${aryn.ai.runtime-tokens.order:}") String token,@Value("${aryn.ai.allowed-private-hosts:}") Set<String> allowedHosts){this.token=token;this.allowedHosts=allowedHosts;}
    public AiRuntimeClient.Session open(AiUsage usage,String task) {
        return new AiRuntimeClient(remote,"order",token,allowedHosts).open(ArynTenantContextHolder.getTenantId(),task,usage);
    }
    public AiRuntimeClient.Session openVersion(AiUsage usage,String task,String version) {
        return new AiRuntimeClient(remote,"order",token,allowedHosts).openVersion(ArynTenantContextHolder.getTenantId(),task,usage,version);
    }
    public boolean available(AiUsage usage) { try{open(usage,"availability-check");return true;}catch(RuntimeException ex){return false;} }
}
