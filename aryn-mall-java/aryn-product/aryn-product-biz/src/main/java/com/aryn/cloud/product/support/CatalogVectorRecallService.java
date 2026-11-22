package com.aryn.cloud.product.support;
import com.aryn.cloud.product.mapper.ReplenishImportMatchMapper;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class CatalogVectorRecallService {
    private final CatalogIndexRepository repository;
    private final ReplenishImportMatchMapper catalog;
    private final EmbeddingClient embedding;
    private final int topK;
    private final double threshold;
    public CatalogVectorRecallService(CatalogIndexRepository repository,ReplenishImportMatchMapper catalog,EmbeddingClient embedding,
            @Value("${aryn.embedding.recall-top-k:6}") int topK,@Value("${aryn.embedding.score-threshold:0.55}") double threshold){
        this.repository=repository;this.catalog=catalog;this.embedding=embedding;this.topK=Math.max(1,Math.min(20,topK));this.threshold=threshold;
    }
    public record VectorMatch(ReplenishImportSkuRow row,double score){ }
    public boolean isAvailable(){return embedding.isAvailable();}
    public List<List<VectorMatch>> recall(String tenant,List<String> names){
        if(names.isEmpty())return List.of();
        var generation=repository.active(tenant);
        if(generation==null || !"READY".equals(generation.getStatus()))return names.stream().map(name->List.<VectorMatch>of()).toList();
        var session=embedding.openVersion(UUID.randomUUID().toString(),generation.getConfigVersion());
        if(!CatalogIndexRules.compatible(generation.getModelFingerprint(),generation.getDimension(),CatalogIndexRules.fingerprint(session.model().providerId()+":"+session.model().model()+":"+session.model().configVersion()),session.model().dimension()))throw new IllegalStateException("目录索引与查询模型不匹配");
        var query=embedding.embed(session,names);List<List<VectorMatch>> scores=new ArrayList<>();for(int i=0;i<names.size();i++)scores.add(new ArrayList<>());
        String cursor=null;
        while(true){
            var items=repository.page(generation.getId(),cursor);if(items.isEmpty())break;
            var live=catalog.selectRowsBySkuIds(tenant,items.stream().map(item->item.getSkuId()).toList());Map<String,ReplenishImportSkuRow> byId=new HashMap<>();
            for(var row:live)if("1".equals(row.getSpuStatus()) && "0".equals(row.getSkuStatus()))byId.put(row.getSkuId(),row);
            for(var item:items){
                var row=byId.get(item.getSkuId());if(row==null || !Objects.equals(row.getName(),item.getSourceName()))continue;
                float[] vector=JSON.parseObject(item.getVectorJson(),float[].class);
                if(vector.length!=generation.getDimension())throw new IllegalStateException("索引向量维度损坏");
                for(int i=0;i<query.size();i++){
                    double score=dot(normalize(query.get(i)),vector);if(score<threshold)continue;
                    var list=scores.get(i);list.add(new VectorMatch(row,score));list.sort(Comparator.comparingDouble(VectorMatch::score).reversed().thenComparing(m->m.row().getSkuId()));
                    // 每页即裁剪，内存只保留候选；每SPU最多2个。
                    Set<String> seen=new HashSet<>();Map<String,Integer> count=new HashMap<>();
                    list.removeIf(m->{String id=m.row().getSpuId();int used=count.merge(id,1,Integer::sum);return used>2 || !seen.add(m.row().getSkuId());});
                    while(list.size()>topK)list.remove(list.size()-1);
                }
            }
            cursor=items.get(items.size()-1).getSkuId();
        }
        return scores;
    }
    static float[] normalize(float[] vector){double sum=0;for(float v:vector){if(!Float.isFinite(v))throw new IllegalArgumentException("向量包含非有限值");sum+=(double)v*v;}if(sum==0)return vector;float[] out=new float[vector.length];for(int i=0;i<out.length;i++)out[i]=(float)(vector[i]/Math.sqrt(sum));return out;}
    static double dot(float[] left,float[] right){if(left.length!=right.length)throw new IllegalArgumentException("向量维度不一致");double result=0;for(int i=0;i<left.length;i++)result+=(double)left[i]*right[i];return result;}
}
