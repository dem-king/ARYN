package com.aryn.cloud.upms.support;
import com.aryn.cloud.upms.config.AiCredentialProperties;
import com.aryn.cloud.upms.api.dto.ai.*;
import com.aryn.cloud.common.ai.contract.AiUsage;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
@Component @RequiredArgsConstructor
public class AiRuntimeAuthorization {
    private final AiCredentialProperties properties;
    private static final Map<String,Set<AiUsage>> ALLOWED=Map.of("order",Set.of(AiUsage.PROCUREMENT_PARSE,AiUsage.SKU_RERANK),"product",Set.of(AiUsage.CATALOG_EMBEDDING),"promotion",Set.of(AiUsage.MARKETING_DRAFT));
    public void require(String action,AiRuntimeRequest request) {
        if(request==null || request.context()==null || request.caller()==null || request.signature()==null || !Objects.equals(ArynTenantContextHolder.getTenantId(),request.context().tenantId())
            || Math.abs(Instant.now().getEpochSecond()-request.issuedAt())>60 || !ALLOWED.getOrDefault(request.caller(),Set.of()).contains(request.context().usage())) throw new SecurityException("AI 内部调用未授权");
        String token=properties.getRuntimeTokens().get(request.caller());
        if(token==null || token.length()<32) throw new SecurityException("AI 内部调用未授权");
        String expected=AiRuntimeSignatures.sign(action,request,token);
        if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),request.signature().getBytes(StandardCharsets.US_ASCII))) throw new SecurityException("AI 内部调用未授权");
    }
}
