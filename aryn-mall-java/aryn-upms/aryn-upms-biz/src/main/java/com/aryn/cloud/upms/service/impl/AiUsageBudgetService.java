package com.aryn.cloud.upms.service.impl;
import com.aryn.cloud.upms.api.dto.ai.*;
import com.aryn.cloud.upms.api.entity.ai.*;
import com.aryn.cloud.upms.mapper.*;
import com.aryn.cloud.upms.config.AiCredentialProperties;
import com.aryn.cloud.common.ai.contract.AiModelSnapshot;
import com.aryn.cloud.common.ai.error.*;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import lombok.RequiredArgsConstructor;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 预算行锁串行化同租户同用途的并发入场；未知token按预留上限计预算，不伪造实耗。 */
@Service @RequiredArgsConstructor
public class AiUsageBudgetService {
    private final AiUsageBudgetMapper budgets;
    private final AiCallLogMapper logs;
    private final AiCredentialProperties properties;
    @Transactional(rollbackFor=Exception.class)
    public boolean begin(AiRuntimeRequest request,AiModelSnapshot model) {
        var context=request.context();String tenant=ArynTenantContextHolder.getTenantId();
        if(!Objects.equals(tenant,context.tenantId())) throw new SecurityException("租户不匹配");
        lock(tenant,context.usage().name());
        String id=callId(tenant,context.callId());
        if(logs.selectById(id)!=null) return false;
        var now=LocalDateTime.now();
        long concurrent=logs.selectCount(Wrappers.<AiCallLog>lambdaQuery().eq(AiCallLog::getUsageCode,context.usage().name()).eq(AiCallLog::getResult,"RUNNING").gt(AiCallLog::getLeaseUntil,now));
        long taskCalls=logs.selectCount(Wrappers.<AiCallLog>lambdaQuery().eq(AiCallLog::getUsageCode,context.usage().name()).eq(AiCallLog::getTaskId,context.taskId()));
        var daily=logs.selectList(Wrappers.<AiCallLog>lambdaQuery().eq(AiCallLog::getUsageCode,context.usage().name()).ge(AiCallLog::getCreateTime,now.toLocalDate().atStartOfDay()));
        long used=0;for(var log:daily) used=Math.addExact(used,log.getTotalTokens()==null?log.getReservedTokens():log.getTotalTokens());
        long reserved=Math.addExact(Math.multiplyExact((long)model.maxInputChars(),4),model.maxOutputTokens());
        if(concurrent>=properties.getMaxConcurrent() || taskCalls>=properties.getMaxTaskCalls() || reserved>properties.getDailyTokenLimit()-used) throw new AiCallException(AiErrorCode.BUDGET_EXCEEDED);
        var log=new AiCallLog();log.setId(id);log.setTenantId(tenant);log.setUsageCode(context.usage().name());log.setTaskId(context.taskId());log.setVersionId(model.configVersion());log.setProviderId(model.providerId());log.setModelId(model.model());
        log.setResult("RUNNING");log.setReservedTokens(reserved);log.setLeaseUntil(now.plusSeconds((model.timeoutMillis()+999)/1000+30));log.setCreateTime(now);log.setUpdateTime(now);log.setDelFlag("0");logs.insert(log);return true;
    }
    @Transactional(rollbackFor=Exception.class)
    public void report(AiUsageReport report) {
        var context=report.authorization().context();String tenant=ArynTenantContextHolder.getTenantId();
        if(!Objects.equals(tenant,context.tenantId())) throw new SecurityException("租户不匹配");
        lock(tenant,context.usage().name());
        String id=callId(tenant,context.callId());var log=logs.selectById(id);
        if(log==null || !log.getTaskId().equals(context.taskId()) || !log.getVersionId().equals(report.authorization().version()) || !log.getProviderId().equals(report.authorization().providerId())) throw new IllegalArgumentException("调用记录不存在或归属不符");
        if(!"RUNNING".equals(log.getResult())) return;
        if(!"SUCCESS".equals(report.result())) { try{AiErrorCode.valueOf(report.result());}catch(Exception ex){throw new IllegalArgumentException("调用结果无效");} }
        if(report.elapsedMillis()<0 || (report.totalTokens()!=null && (report.totalTokens()<0 || report.totalTokens()>log.getReservedTokens()))) throw new IllegalArgumentException("用量数据无效");
        log.setResult(report.result());log.setTotalTokens(report.totalTokens());log.setElapsedMillis(report.elapsedMillis());log.setUpdateTime(LocalDateTime.now());logs.updateById(log);
    }
    private void lock(String tenant,String usage) {
        String id=callId(tenant,"budget:"+usage);
        var existing=budgets.selectById(id);
        if(existing==null) {
            var row=new AiUsageBudget();row.setId(id);row.setTenantId(tenant);row.setUsageCode(usage);row.setDelFlag("0");row.setCreateTime(LocalDateTime.now());row.setUpdateTime(LocalDateTime.now());
            try{budgets.insert(row);}catch(DuplicateKeyException ignored){ }
        }
        if(budgets.selectOne(Wrappers.<AiUsageBudget>lambdaQuery().eq(AiUsageBudget::getId,id).last("FOR UPDATE"))==null) throw new IllegalStateException("预算锁失败");
    }
    static String callId(String tenant,String call) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((tenant.length()+":"+tenant+call).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalStateException();}
    }
}
