package com.aryn.cloud.product.support;
import com.aryn.cloud.product.api.entity.*;
import com.aryn.cloud.product.mapper.ReplenishImportMatchMapper;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import java.util.*;
@Service @RequiredArgsConstructor
public class CatalogIndexService {
    private final CatalogIndexRepository repository;
    private final ReplenishImportMatchMapper catalog;
    private final EmbeddingClient embedding;
    public record Status(String activeGeneration,long revision,List<CatalogVectorGeneration> generations){ }
    public Status status(){var state=repository.state(requireTenant());return new Status(state==null?null:state.getGenerationId(),state==null?0:state.getRevision(),list());}
    public void retry(String id){repository.retry(id);}
    public List<CatalogVectorGeneration> list(){return repository.list();}
    public CatalogVectorGeneration create(){
        String tenant=requireTenant();var session=embedding.open(UUID.randomUUID().toString());var model=session.model();
        var generation=new CatalogVectorGeneration();generation.setId(UUID.randomUUID().toString());generation.setTenantId(tenant);generation.setConfigVersion(model.configVersion());
        generation.setModelFingerprint(CatalogIndexRules.fingerprint(model.providerId()+":"+model.model()+":"+model.configVersion()));generation.setDimension(model.dimension());
        generation.setStatus("QUEUED");generation.setIndexedRows(0);generation.setExpectedRows(catalog.countCatalogRows(tenant));generation.setDelFlag("0");generation.setCreateTime(LocalDateTime.now());generation.setUpdateTime(LocalDateTime.now());repository.insert(generation);return generation;
    }
    /** 商品新增、改名或下架后准备新一代，切换仍由管理员决定。 */
    public void refreshIfChanged() {
        String tenant = requireTenant();
        var active = repository.active(tenant);
        if (active == null) return;
        var generations = list();
        if (generations.stream().anyMatch(g -> Set.of("QUEUED", "BUILDING").contains(g.getStatus()))) return;
        List<String> current = new ArrayList<>();
        String cursor = null;
        while (true) {
            var page = catalog.selectCatalogCursor(tenant, cursor, 500);
            if (page.isEmpty()) break;
            for (var row : page) current.add(row.getSkuId() + ":" + row.getName());
            cursor = page.get(page.size() - 1).getSkuId();
        }
        String fingerprint = CatalogIndexRules.fingerprint(JSON.toJSONString(current));
        if (generations.stream().noneMatch(g -> "READY".equals(g.getStatus()) && fingerprint.equals(g.getCatalogFingerprint()))) create();
    }
    public void build(String id){
        String tenant=requireTenant();String token=repository.claim(id);if(token==null)return;
        try{
            var generation=repository.get(id);var session=embedding.openVersion(id,generation.getConfigVersion());
            if(!CatalogIndexRules.compatible(generation.getModelFingerprint(),generation.getDimension(),CatalogIndexRules.fingerprint(session.model().providerId()+":"+session.model().model()+":"+session.model().configVersion()),session.model().dimension())) throw new IllegalStateException("向量空间不一致");
            String cursor=generation.getCursorId();
            while(true){
                var page=catalog.selectCatalogCursor(tenant,cursor,32);if(page.isEmpty())break;
                var vectors=embedding.embed(session,page.stream().map(ReplenishImportSkuRow::getName).toList());List<CatalogVectorItem> items=new ArrayList<>();
                for(int i=0;i<page.size();i++){
                    var row=page.get(i);var item=new CatalogVectorItem();item.setId(CatalogIndexRules.fingerprint(id+":"+row.getSkuId()));item.setGenerationId(id);item.setSkuId(row.getSkuId());item.setSourceName(row.getName());
                    item.setVectorJson(JSON.toJSONString(CatalogVectorRecallService.normalize(vectors.get(i))));item.setTenantId(tenant);item.setDelFlag("0");item.setCreateTime(LocalDateTime.now());item.setUpdateTime(LocalDateTime.now());items.add(item);
                }
                cursor=page.get(page.size()-1).getSkuId();repository.savePage(id,token,items,cursor);
            }
            List<String> persisted=new ArrayList<>();String itemCursor=null;
            while(true){var page=repository.page(id,itemCursor);if(page.isEmpty())break;for(var item:page)persisted.add(item.getSkuId()+":"+item.getSourceName());itemCursor=page.get(page.size()-1).getSkuId();}
            List<String> current=new ArrayList<>();String sourceCursor=null;
            while(true){var page=catalog.selectCatalogCursor(tenant,sourceCursor,500);if(page.isEmpty())break;for(var item:page)current.add(item.getSkuId()+":"+item.getName());sourceCursor=page.get(page.size()-1).getSkuId();}
            if(!persisted.equals(current))throw new IllegalStateException("构建期间商品目录发生变化，请重新构建");
            repository.finish(id,token,CatalogIndexRules.fingerprint(JSON.toJSONString(current)),current.size());
        }catch(RuntimeException ex){repository.fail(id,token);}
    }
    public void activate(String id,long expected){
        var generation=repository.get(id);if(generation==null)throw new IllegalArgumentException("索引不存在");
        // 确认引用版本仍可解析，紧急停用不能绕过。
        var session=embedding.openVersion("index-activation",generation.getConfigVersion());
        if(session.model().dimension()!=generation.getDimension().intValue())throw new IllegalArgumentException("索引维度不匹配");
        repository.activate(requireTenant(),id,expected);
    }
    private String requireTenant(){String tenant=ArynTenantContextHolder.getTenantId();if(tenant==null || tenant.isBlank())throw new SecurityException("租户上下文不存在");return tenant;}
}
