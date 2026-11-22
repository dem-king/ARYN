package com.aryn.cloud.upms.support;

import com.aryn.cloud.upms.mapper.*;
import com.aryn.cloud.upms.api.entity.ai.*;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

/** 所有配置操作限定平台租户；调用方在进入此仓储前完成平台身份或专用RPC校验。 */
@Repository @RequiredArgsConstructor
public class AiConfigRepository {
    private final SysCopyrightConfigMapper platformLookup;
    private final AiConfigVersionMapper versions;
    private final AiCredentialVersionMapper credentials;
    private final AiRuntimeStateMapper states;
    private final AiCapabilityTestMapper tests;
    private final AiConfigOperationMapper operations;
    private final AiCallLogMapper calls;
    public <T> T inPlatform(Supplier<T> work) {
        String before=ArynTenantContextHolder.getTenantId();
        String platform=platformLookup.selectPlatformTenantId();
        if(platform==null || platform.isBlank()) throw new IllegalStateException("平台租户不存在");
        ArynTenantContextHolder.setTenantId(platform);
        try { return work.get(); }
        finally { if(before==null) ArynTenantContextHolder.removeTenantId(); else ArynTenantContextHolder.setTenantId(before); }
    }
    public AiRuntimeState state(boolean lock) {
        String tenant=ArynTenantContextHolder.getTenantId();
        var query=Wrappers.<AiRuntimeState>lambdaQuery().eq(AiRuntimeState::getId,tenant);
        if(lock) query.last("FOR UPDATE");
        AiRuntimeState state=states.selectOne(query);
        if(state==null) {
            state=new AiRuntimeState();state.setId(tenant);state.setTenantId(tenant);state.setDelFlag("0");
            state.setRevision(0L);state.setEmergencyEpoch(0L);state.setDisabled(false);state.setDisabledProviders("[]");
            state.setCreateTime(LocalDateTime.now());state.setUpdateTime(LocalDateTime.now());
            try { states.insert(state); } catch(DuplicateKeyException ignored) { }
            state=states.selectOne(query);
        }
        return state;
    }
    public void updateState(AiRuntimeState state,long expected) {
        state.setUpdateTime(LocalDateTime.now());
        int changed=states.update(state,Wrappers.<AiRuntimeState>lambdaUpdate().eq(AiRuntimeState::getId,state.getId()).eq(AiRuntimeState::getRevision,expected));
        if(changed!=1) throw new IllegalStateException("配置版本冲突，请重新加载");
    }
    public AiConfigVersion version(String id) {
        AiConfigVersion version=id==null?null:versions.selectById(id);
        if(version==null) throw new IllegalArgumentException("AI 配置版本不存在"); return version;
    }
    public void insertVersion(AiConfigVersion version) { versions.insert(version); }
    public void insertCredential(AiCredentialVersion credential) { credentials.insert(credential); }
    public AiCredentialVersion credential(String id) {
        AiCredentialVersion credential=credentials.selectById(id);
        if(credential==null) throw new IllegalStateException("AI 凭证版本不存在"); return credential;
    }
    @Transactional(rollbackFor=Exception.class)
    public void recordTest(AiCapabilityTest test) { inPlatform(()->{ tests.insert(test); return null; }); }
    public boolean tested(String version,String model,LocalDateTime after) {
        return tests.selectCount(Wrappers.<AiCapabilityTest>lambdaQuery().eq(AiCapabilityTest::getVersionId,version)
            .eq(AiCapabilityTest::getModelId,model).eq(AiCapabilityTest::getPassed,true).ge(AiCapabilityTest::getCreateTime,after))>0;
    }
    public List<AiCapabilityTest> recentTests(String version, LocalDateTime after) {
        return tests.selectList(Wrappers.<AiCapabilityTest>lambdaQuery()
            .eq(AiCapabilityTest::getVersionId, version)
            .ge(AiCapabilityTest::getCreateTime, after)
            .orderByDesc(AiCapabilityTest::getCreateTime, AiCapabilityTest::getId));
    }
    public List<AiConfigVersion> versions() { return versions.selectList(Wrappers.<AiConfigVersion>lambdaQuery().orderByDesc(AiConfigVersion::getCreateTime).last("LIMIT 100")); }
    public boolean wasActivated(String version) {
        return operations.selectCount(Wrappers.<AiConfigOperation>lambdaQuery().eq(AiConfigOperation::getVersionId,version).in(AiConfigOperation::getOperation,List.of("ACTIVATE","ROLLBACK")))>0;
    }
    public void operation(String operation,String version,String previous,String actor) {
        AiConfigOperation entry=new AiConfigOperation();entry.setId(UUID.randomUUID().toString());entry.setTenantId(ArynTenantContextHolder.getTenantId());
        entry.setOperation(operation);entry.setVersionId(version);entry.setPreviousVersion(previous);entry.setCreateBy(actor);entry.setDelFlag("0");entry.setCreateTime(LocalDateTime.now());
        operations.insert(entry);
    }
    /** 仅供通过平台权限检查的调用记录读接口使用，保留租户拦截器。 */
    public List<AiCallLog> callLogs(String tenant) {
        if (tenant == null || !tenant.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("请选择合法的调用租户");
        String before = ArynTenantContextHolder.getTenantId();
        ArynTenantContextHolder.setTenantId(tenant);
        try {
            return calls.selectList(Wrappers.<AiCallLog>lambdaQuery().eq(AiCallLog::getTenantId, tenant)
                .orderByDesc(AiCallLog::getCreateTime).last("LIMIT 100"));
        } finally {
            if (before == null) ArynTenantContextHolder.removeTenantId();
            else ArynTenantContextHolder.setTenantId(before);
        }
    }
}
