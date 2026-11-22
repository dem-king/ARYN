package com.aryn.cloud.upms.service.impl;
import com.aryn.cloud.upms.api.dto.ai.*;
import com.aryn.cloud.upms.support.*;
import com.aryn.cloud.common.ai.contract.AiModelSnapshot;
import com.aryn.cloud.common.ai.error.*;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class AiRuntimeService {
    private final AiConfigRepository repository;
    private final AiConfigServiceImpl configuration;
    private final AiRuntimeAuthorization authorization;
    private final AiUsageBudgetService budget;
    public AiResolvedSnapshot resolve(AiRuntimeRequest request) {
        authorization.require("resolve",request);
        return repository.inPlatform(()->{
            var state=repository.state(false);if(Boolean.TRUE.equals(state.getDisabled()) || state.getActiveVersion()==null) throw disabled();
            String version=request.version()==null?state.getActiveVersion():request.version();
            if(!version.equals(state.getActiveVersion()) && !repository.wasActivated(version)) throw disabled();
            var model=model(version,request);
            if(configuration.disabledProviders(state).contains(model.providerId())) throw disabled();
            return new AiResolvedSnapshot(model,state.getEmergencyEpoch());
        });
    }
    public String currentVersion(AiRuntimeRequest request) {
        authorization.require("current",request);
        return repository.inPlatform(()->repository.state(false).getActiveVersion());
    }
    public void checkRuntimeState(AiRuntimeRequest request) { authorization.require("check",request);check(request); }
    public boolean beginCall(AiRuntimeRequest request) {
        authorization.require("begin",request);
        AiModelSnapshot model=check(request);return budget.begin(request,model);
    }
    public void reportUsage(AiUsageReport report) {
        authorization.require(AiRuntimeSignatures.reportAction(report),report.authorization());budget.report(report);
    }
    private AiModelSnapshot check(AiRuntimeRequest request) {
        return repository.inPlatform(()->{
            var state=repository.state(false);
            if(request.version()==null || Boolean.TRUE.equals(state.getDisabled()) || state.getEmergencyEpoch()!=request.emergencyEpoch() || configuration.disabledProviders(state).contains(request.providerId())) throw disabled();
            if(!request.version().equals(state.getActiveVersion()) && !repository.wasActivated(request.version())) throw disabled();
            var model=model(request.version(),request);
            if(!model.providerId().equals(request.providerId())) throw new SecurityException("AI 配置供应商不匹配");return model;
        });
    }
    private AiModelSnapshot model(String version,AiRuntimeRequest request) {
        var config=configuration.configuration(version);
        String modelId=config.bindings().get(request.context().usage());if(modelId==null) throw disabled();return configuration.snapshot(version,modelId);
    }
    private AiCallException disabled(){return new AiCallException(AiErrorCode.DISABLED);}
}
