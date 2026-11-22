package com.aryn.cloud.upms.dubbo;
import com.aryn.cloud.upms.api.remote.RemoteAiConfigService;
import com.aryn.cloud.upms.api.dto.ai.*;
import com.aryn.cloud.upms.service.impl.AiRuntimeService;
import org.apache.dubbo.config.annotation.DubboService;
import lombok.RequiredArgsConstructor;
@DubboService @RequiredArgsConstructor
public class RemoteAiConfigServiceImpl implements RemoteAiConfigService {
    private final AiRuntimeService service;
    public AiResolvedSnapshot resolve(AiRuntimeRequest request){return service.resolve(request);}
    public String currentVersion(AiRuntimeRequest request){return service.currentVersion(request);}
    public void checkRuntimeState(AiRuntimeRequest request){service.checkRuntimeState(request);}
    public boolean beginCall(AiRuntimeRequest request){return service.beginCall(request);}
    public void reportUsage(AiUsageReport report){service.reportUsage(report);}
}
