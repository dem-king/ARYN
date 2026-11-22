package com.aryn.cloud.upms.service.impl;

import com.aryn.cloud.upms.support.*;
import com.aryn.cloud.upms.config.AiCredentialProperties;
import com.aryn.cloud.upms.api.dto.ai.*;
import com.aryn.cloud.upms.api.entity.ai.*;
import com.aryn.cloud.common.ai.contract.*;
import com.aryn.cloud.common.ai.client.AiProtocolClient;
import com.aryn.cloud.common.ai.protocol.AiEndpointPolicy;
import com.aryn.cloud.common.ai.error.AiCallException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import lombok.RequiredArgsConstructor;
import java.net.URI;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service @RequiredArgsConstructor
public class AiConfigServiceImpl {
    private final AiConfigRepository repository;
    private final AiConfigAuthorization authorization;
    private final AiCredentialCipher cipher;
    private final AiCredentialProperties properties;
    private final Optional<StringRedisTemplate> redis;
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    public AiConfigView current() {
        authorization.require("query");
        return repository.inPlatform(()->view(repository.state(false),null));
    }
    public List<AiConfigView> versions() {
        authorization.require("query");
        return repository.inPlatform(()->{var state=repository.state(false);return repository.versions().stream().map(v->view(state,v.getId())).toList();});
    }
    public List<AiConfigTestResult> testResults(String version) {
        authorization.require("query");
        return repository.inPlatform(() -> {
            repository.version(version);
            var byModel = new LinkedHashMap<String, AiConfigTestResult>();
            for (var row : repository.recentTests(version, LocalDateTime.now().minusHours(24))) {
                var result = new AiConfigTestResult(version, row.getModelId(), Boolean.TRUE.equals(row.getPassed()),
                    row.getStatus(), row.getCreateTime().atZone(ZoneId.systemDefault()).toInstant());
                // 启用检查接受 24 小时内任意一次成功测试。
                byModel.merge(row.getModelId(), result, (latest, older) ->
                    latest.passed() || !older.passed() ? latest : older);
            }
            return List.copyOf(byModel.values());
        });
    }
    @Transactional(rollbackFor=Exception.class)
    public AiConfigView save(AiConfigSaveCommand command) {
        String actor=authorization.require("edit").getUserId();
        return repository.inPlatform(()->{
            var state=repository.state(true);checkRevision(state,command.expectedRevision());
            AiConfiguration previous=configuration(state.getDraftVersion());
            if(command.providers()==null || command.models()==null || command.bindings()==null || command.providers().size()>20 || command.models().size()>100) throw new IllegalArgumentException("AI 配置大小无效");
            List<AiProvider> providers=new ArrayList<>();Set<String> ids=new HashSet<>();
            for(var input:command.providers()) {
                identifier(input.id());
                if(!ids.add(input.id()) || input.name()==null || input.name().isBlank() || input.name().length()>100 || !"OPENAI_COMPATIBLE".equals(input.protocol())) throw new IllegalArgumentException("供应商配置无效");
                new AiEndpointPolicy(properties.getAllowedPrivateHosts()).validate(URI.create(input.endpoint()));
                var old=previous.providers().stream().filter(provider->provider.id().equals(input.id())).findFirst().orElse(null);
                String credential=old==null?null:old.credentialVersion();
                if(input.keyOperation()==null) throw new IllegalArgumentException("请选择密钥操作");
                if(input.keyOperation()!=AiConfigSaveCommand.KeyOperation.REPLACE && input.apiKey()!=null && !input.apiKey().isBlank()) throw new IllegalArgumentException("仅替换密钥时允许提交 API Key");
                switch(input.keyOperation()) {
                    case KEEP -> { }
                    case CLEAR -> credential=null;
                    case REPLACE -> {
                        credential=UUID.randomUUID().toString();
                        var row=new AiCredentialVersion();row.setId(credential);row.setTenantId(state.getTenantId());row.setProviderId(input.id());
                        row.setSealedValue(cipher.encrypt(input.apiKey(),state.getTenantId(),input.id(),credential,"AI_PROVIDER"));
                        row.setDelFlag("0");row.setCreateBy(actor);row.setCreateTime(LocalDateTime.now());repository.insertCredential(row);
                    }
                }
                providers.add(new AiProvider(input.id(),input.name(),input.protocol(),input.endpoint(),credential,input.enabled()));
            }
            AiConfiguration config=new AiConfiguration(providers,command.models(),command.bindings());validate(config);
            String id=UUID.randomUUID().toString();String document=encode(config);
            var version=new AiConfigVersion();version.setId(id);version.setTenantId(state.getTenantId());version.setDocument(document);version.setDigest(digest(document));
            version.setRevision(state.getRevision()+1);version.setCreateBy(actor);version.setCreateTime(LocalDateTime.now());version.setDelFlag("0");repository.insertVersion(version);
            long before=state.getRevision();state.setRevision(before+1);state.setDraftVersion(id);state.setUpdateBy(actor);repository.updateState(state,before);
            repository.operation("SAVE_DRAFT",id,null,actor);return view(state,id);
        });
    }
    /** 外呼不在数据库事务内；只在测试结果落库时开启短事务。 */
    public AiConfigTestResult test(String version,String modelId) {
        String actor=authorization.require("test").getUserId();
        AiModelSnapshot snapshot=repository.inPlatform(()->{
            var state=repository.state(false);var model=snapshot(version,modelId);
            if(Boolean.TRUE.equals(state.getDisabled()) || disabledProviders(state).contains(model.providerId())) throw new IllegalStateException("AI 已紧急停用，不能发起能力测试");
            return model;
        });
        boolean passed=false;String result="UNAVAILABLE";
        try {
            var client=new AiProtocolClient(new AiEndpointPolicy(properties.getAllowedPrivateHosts()),1048576);
            var context=new AiCallContext(UUID.randomUUID().toString(),"capability-test",repository.inPlatform(()->repository.state(false).getTenantId()),snapshot.capability()==AiCapability.EMBEDDING?AiUsage.CATALOG_EMBEDDING:AiUsage.PROCUREMENT_PARSE);
            if(snapshot.capability()==AiCapability.STRUCTURED_TEXT) {
                var output=client.text(snapshot,context,"Return exactly a JSON object with field ok=true.","Fixed protocol test; no customer data.",Set.of("ok"));
                if(!output.output().path("ok").isBoolean() || !output.output().path("ok").asBoolean()) throw new IllegalArgumentException("结构化样本不合格");
            } else client.embed(snapshot,context,List.of("fixed catalog capability sample"));
            passed=true;result="PASSED";
        } catch(AiCallException ex) { result=ex.code().name(); }
        catch(IllegalArgumentException ex) { result="INVALID_RESULT"; }
        var row=new AiCapabilityTest();row.setId(UUID.randomUUID().toString());row.setVersionId(version);row.setModelId(modelId);row.setPassed(passed);row.setStatus(result);
        row.setTenantId(repository.inPlatform(()->repository.state(false).getTenantId()));row.setCreateBy(actor);row.setDelFlag("0");row.setCreateTime(LocalDateTime.now());repository.recordTest(row);
        return new AiConfigTestResult(version,modelId,passed,result,Instant.now());
    }
    @Transactional(rollbackFor=Exception.class)
    public AiConfigView activate(String version,long expectedRevision,boolean rollback) {
        String actor=authorization.require("activate").getUserId();
        return repository.inPlatform(()->{
            var state=repository.state(true);checkRevision(state,expectedRevision);
            var config=configuration(version);validate(config);
            if(config.bindings().isEmpty()) throw new IllegalArgumentException("请选择至少一个用途模型");
            for(String modelId:new HashSet<>(config.bindings().values())) {
                snapshot(version,modelId);
                if(!repository.tested(version,modelId,LocalDateTime.now().minusHours(24))) throw new IllegalStateException("启用前请测试当前版本的每个用途模型（测试24小时有效）");
            }
            String previous=state.getActiveVersion();long before=state.getRevision();state.setActiveVersion(version);state.setRevision(before+1);state.setUpdateBy(actor);
            repository.updateState(state,before);repository.operation(rollback?"ROLLBACK":"ACTIVATE",version,previous,actor);notifyAfterCommit(version);return view(state,state.getDraftVersion());
        });
    }
    @Transactional(rollbackFor=Exception.class)
    public AiConfigView emergency(String providerId,boolean disabled,long expectedRevision) {
        String actor=authorization.require("activate").getUserId();
        return repository.inPlatform(()->{
            var state=repository.state(true);checkRevision(state,expectedRevision);long before=state.getRevision();
            if(providerId==null) state.setDisabled(disabled);
            else {
                identifier(providerId);Set<String> providers=new HashSet<>(disabledProviders(state));
                if(disabled) providers.add(providerId); else providers.remove(providerId);state.setDisabledProviders(encode(providers));
            }
            state.setEmergencyEpoch(state.getEmergencyEpoch()+1);state.setRevision(before+1);state.setUpdateBy(actor);repository.updateState(state,before);
            repository.operation(disabled?"EMERGENCY_DISABLE":"MANUAL_RESUME",state.getActiveVersion(),providerId,actor);notifyAfterCommit(state.getActiveVersion());return view(state,null);
        });
    }
    public List<AiCallLog> logs(String tenant) {
        authorization.require("query");
        return repository.callLogs(tenant == null || tenant.isBlank()
            ? com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder.getTenantId() : tenant);
    }
    public AiConfiguration configuration(String id) {
        if(id==null) return new AiConfiguration(List.of(),List.of(),Map.of());
        try{return json.readValue(repository.version(id).getDocument(),AiConfiguration.class);}
        catch(Exception ex){throw new IllegalStateException("AI 配置版本损坏");}
    }
    /** 仅供完成认证的领域运行适配器调用；Controller永不返回快照。 */
    public AiModelSnapshot snapshot(String version,String modelId) {
        var config=configuration(version);var model=config.models().stream().filter(m->m.id().equals(modelId)).findFirst().orElseThrow(()->new IllegalArgumentException("模型不存在"));
        var provider=config.providers().stream().filter(p->p.id().equals(model.providerId())).findFirst().orElseThrow(()->new IllegalArgumentException("供应商不存在"));
        if(!provider.enabled() || provider.credentialVersion()==null) throw new IllegalStateException("模型供应商已禁用或未配置密钥");
        var credential=repository.credential(provider.credentialVersion());
        if(!provider.id().equals(credential.getProviderId())) throw new IllegalStateException("凭证归属不匹配");
        return new AiModelSnapshot(version,credential.getId(),provider.id(),URI.create(provider.endpoint()),model.model(),model.capability(),model.timeoutMillis(),model.maxInputChars(),model.maxOutputTokens(),model.dimension(),model.parameters(),
            new AiSecret(cipher.decrypt(credential.getSealedValue(),credential.getTenantId(),provider.id(),credential.getId(),"AI_PROVIDER")));
    }
    public Set<String> disabledProviders(AiRuntimeState state) {
        try{return Set.copyOf(json.readValue(state.getDisabledProviders(),json.getTypeFactory().constructCollectionType(Set.class,String.class)));}
        catch(Exception ex){throw new IllegalStateException("AI 停用状态损坏");}
    }
    private AiConfigView view(AiRuntimeState state,String id) {
        String version=id==null?state.getDraftVersion():id;
        return new AiConfigView(version,state.getRevision(),state.getActiveVersion(),state.getEmergencyEpoch(),Boolean.TRUE.equals(state.getDisabled()),disabledProviders(state),configuration(version));
    }
    private void validate(AiConfiguration config) {
        Set<String> providerIds=new HashSet<>();for(var p:config.providers()) providerIds.add(p.id());Set<String> models=new HashSet<>();
        for(var model:config.models()) {
            identifier(model.id());
            if(!models.add(model.id()) || !providerIds.contains(model.providerId())) throw new IllegalArgumentException("模型重复或供应商不存在");
            if(model.model()==null || model.model().length()>200) throw new IllegalArgumentException("模型ID无效");
            new AiModelSnapshot("validation","validation",model.providerId(),URI.create("https://example.com"),model.model(),model.capability(),model.timeoutMillis(),model.maxInputChars(),model.maxOutputTokens(),model.dimension(),model.parameters(),new AiSecret("validation"));
        }
        for(var binding:config.bindings().entrySet()) {
            var model=config.models().stream().filter(m->m.id().equals(binding.getValue())).findFirst().orElseThrow(()->new IllegalArgumentException("用途模型不存在"));
            if(binding.getKey().capability()!=model.capability()) throw new IllegalArgumentException("用途与模型能力不匹配");
        }
    }
    private void checkRevision(AiRuntimeState state,long expected) { if(state.getRevision()!=expected) throw new IllegalStateException("配置版本冲突，请重新加载"); }
    private void identifier(String id) { if(id==null || !id.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("配置标识无效"); }
    private String encode(Object value) { try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalArgumentException("配置不能序列化");} }
    private String digest(String value) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException();} }
    private void notifyAfterCommit(String version) {
        Runnable send=()->redis.ifPresent(template->{try{template.convertAndSend("aryn:ai:config-version",version==null?"DISABLED":version);}catch(Exception ignored){/* 广播仅优化；新任务和每次外呼都查权威状态。 */}});
        if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){send.run();}}); else send.run();
    }
}
