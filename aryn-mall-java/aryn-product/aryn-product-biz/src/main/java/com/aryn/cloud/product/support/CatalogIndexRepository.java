package com.aryn.cloud.product.support;
import com.aryn.cloud.product.api.entity.*;
import com.aryn.cloud.product.mapper.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import java.util.*;
@Repository @RequiredArgsConstructor
public class CatalogIndexRepository {
    private final CatalogVectorGenerationMapper generations;
    private final CatalogVectorItemMapper items;
    private final CatalogVectorStateMapper states;
    public CatalogVectorGeneration get(String id){return generations.selectById(id);}
    public List<CatalogVectorGeneration> list(){return generations.selectList(Wrappers.<CatalogVectorGeneration>lambdaQuery().orderByDesc(CatalogVectorGeneration::getCreateTime).last("LIMIT 100"));}
    public void insert(CatalogVectorGeneration generation){generations.insert(generation);}
    public CatalogVectorState state(String tenant){return states.selectById(tenant);}
    public void retry(String id){int changed=generations.update(null,Wrappers.<CatalogVectorGeneration>lambdaUpdate().eq(CatalogVectorGeneration::getId,id).eq(CatalogVectorGeneration::getStatus,"FAILED").set(CatalogVectorGeneration::getStatus,"QUEUED").set(CatalogVectorGeneration::getLeaseUntil,null).set(CatalogVectorGeneration::getLeaseToken,null).set(CatalogVectorGeneration::getErrorCode,null));if(changed!=1)throw new IllegalStateException("仅失败索引允许重试");}
    public CatalogVectorGeneration active(String tenant){var state=states.selectById(tenant);return state==null?null:get(state.getGenerationId());}
    public List<CatalogVectorItem> page(String generation,String cursor){return items.selectList(Wrappers.<CatalogVectorItem>lambdaQuery().eq(CatalogVectorItem::getGenerationId,generation).gt(cursor!=null,CatalogVectorItem::getSkuId,cursor).orderByAsc(CatalogVectorItem::getSkuId).last("LIMIT 500"));}
    public String claim(String id){String token=UUID.randomUUID().toString();int count=generations.update(null,Wrappers.<CatalogVectorGeneration>lambdaUpdate().eq(CatalogVectorGeneration::getId,id)
        .in(CatalogVectorGeneration::getStatus,List.of("QUEUED","BUILDING")).and(q->q.isNull(CatalogVectorGeneration::getLeaseUntil).or().lt(CatalogVectorGeneration::getLeaseUntil,LocalDateTime.now()))
        .set(CatalogVectorGeneration::getStatus,"BUILDING").set(CatalogVectorGeneration::getLeaseToken,token).set(CatalogVectorGeneration::getLeaseUntil,LocalDateTime.now().plusMinutes(5)));return count==1?token:null;}
    @Transactional(rollbackFor=Exception.class)
    public void savePage(String id,String token,List<CatalogVectorItem> page,String cursor){
        var generation=locked(id,token);
        for(var item:page) items.insert(item);
        generation.setCursorId(cursor);generation.setIndexedRows(generation.getIndexedRows()+page.size());generation.setUpdateTime(LocalDateTime.now());
        generation.setLeaseUntil(LocalDateTime.now().plusMinutes(5));generations.updateById(generation);
    }
    @Transactional(rollbackFor=Exception.class)
    public void finish(String id,String token,String fingerprint,int expected){
        var generation=locked(id,token);
        if(generation.getIndexedRows()!=expected) throw new IllegalStateException("目录覆盖数不一致");
        generation.setCatalogFingerprint(fingerprint);generation.setExpectedRows(expected);generation.setStatus("READY");generation.setUpdateTime(LocalDateTime.now());generations.updateById(generation);
    }
    public void fail(String id,String token){generations.update(null,Wrappers.<CatalogVectorGeneration>lambdaUpdate().eq(CatalogVectorGeneration::getId,id).eq(CatalogVectorGeneration::getLeaseToken,token).set(CatalogVectorGeneration::getStatus,"FAILED").set(CatalogVectorGeneration::getErrorCode,"BUILD_FAILED"));}
    @Transactional(rollbackFor=Exception.class)
    public void activate(String tenant,String generationId,long expected){
        var generation=generations.selectOne(Wrappers.<CatalogVectorGeneration>lambdaQuery().eq(CatalogVectorGeneration::getId,generationId).last("FOR UPDATE"));
        if(generation==null || !"READY".equals(generation.getStatus()) || !tenant.equals(generation.getTenantId())) throw new IllegalArgumentException("索引未准备完成");
        var state=states.selectOne(Wrappers.<CatalogVectorState>lambdaQuery().eq(CatalogVectorState::getId,tenant).last("FOR UPDATE"));
        if(state==null){state=new CatalogVectorState();state.setId(tenant);state.setTenantId(tenant);state.setRevision(0L);state.setDelFlag("0");state.setCreateTime(LocalDateTime.now());state.setUpdateTime(LocalDateTime.now());try{states.insert(state);}catch(DuplicateKeyException ex){throw new IllegalStateException("索引版本冲突");}}
        if(state.getRevision()!=expected) throw new IllegalStateException("索引版本冲突");
        int count=states.update(null,Wrappers.<CatalogVectorState>lambdaUpdate().eq(CatalogVectorState::getId,tenant).eq(CatalogVectorState::getRevision,expected).set(CatalogVectorState::getGenerationId,generationId).set(CatalogVectorState::getRevision,expected+1).set(CatalogVectorState::getUpdateTime,LocalDateTime.now()));
        if(count!=1) throw new IllegalStateException("索引版本冲突");
    }
    private CatalogVectorGeneration locked(String id,String token){
        var generation=generations.selectOne(Wrappers.<CatalogVectorGeneration>lambdaQuery().eq(CatalogVectorGeneration::getId,id).last("FOR UPDATE"));
        if(generation==null || !"BUILDING".equals(generation.getStatus()) || !token.equals(generation.getLeaseToken()) || generation.getLeaseUntil().isBefore(LocalDateTime.now())) throw new IllegalStateException("索引构建租约已失效");return generation;
    }
}
