package com.aryn.cloud.product.support;
import com.aryn.cloud.upms.api.client.AiRuntimeClient;
import com.aryn.cloud.upms.api.remote.RemoteAiConfigService;
import com.aryn.cloud.common.ai.contract.AiUsage;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class ProductAiRuntime {
    @DubboReference(check=false) private RemoteAiConfigService remote;
    private final String token; private final Set<String> allowedHosts;
    public ProductAiRuntime(@Value("${aryn.ai.runtime-tokens.product:}") String token,@Value("${aryn.ai.allowed-private-hosts:}") Set<String> allowedHosts){this.token=token;this.allowedHosts=allowedHosts;}
    public AiRuntimeClient.Session open(AiUsage usage,String task) {
        return new AiRuntimeClient(remote,"product",token,allowedHosts).open(ArynTenantContextHolder.getTenantId(),task,usage);
    }
    public AiRuntimeClient.Session openVersion(AiUsage usage,String task,String version) {
        return new AiRuntimeClient(remote,"product",token,allowedHosts).openVersion(ArynTenantContextHolder.getTenantId(),task,usage,version);
    }
    public boolean available(AiUsage usage) { try{open(usage,"availability-check");return true;}catch(RuntimeException ex){return false;} }
}
