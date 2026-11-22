package com.aryn.cloud.upms.service.impl;
import com.aryn.cloud.upms.api.dto.ai.*;
import com.aryn.cloud.common.ai.contract.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.*;
@Service @RequiredArgsConstructor
public class AiLegacyBootstrapService {
    private final AiConfigServiceImpl configuration;
    private final Environment environment;
    public AiConfigView importDraft(long expectedRevision,boolean embedding){
        var current=configuration.current();
        if(current.revision()!=expectedRevision || current.activeVersion()!=null)throw new IllegalStateException("已有有效配置或版本冲突，禁止环境变量覆盖");
        String prefix=embedding?"AI_EMBEDDING_":"AI_";String id=embedding?"legacy_embedding":"legacy_text";
        if(current.configuration().providers().stream().anyMatch(p->p.id().equals(id)))throw new IllegalStateException("此来源已导入，直接编辑草稿");
        List<AiConfigSaveCommand.ProviderCommand> providers=new ArrayList<>();
        for(var p:current.configuration().providers())providers.add(new AiConfigSaveCommand.ProviderCommand(p.id(),p.name(),p.protocol(),p.endpoint(),p.enabled(),AiConfigSaveCommand.KeyOperation.KEEP,null));
        String key=environment.getProperty(prefix+"API_KEY");if(key==null || key.isBlank())throw new IllegalStateException("部署中未配置旧AI密钥");
        providers.add(new AiConfigSaveCommand.ProviderCommand(id,embedding?"旧向量供应商":"旧文本供应商","OPENAI_COMPATIBLE",environment.getProperty(prefix+"BASE_URL",embedding?"https://open.bigmodel.cn/api/paas/v4":"https://api.deepseek.com"),true,AiConfigSaveCommand.KeyOperation.REPLACE,key));
        var models=new ArrayList<>(current.configuration().models());String modelId=id+"_model";
        models.add(new AiModel(modelId,id,environment.getProperty(prefix+"MODEL",embedding?"embedding-3":"deepseek-chat"),embedding?AiCapability.EMBEDDING:AiCapability.STRUCTURED_TEXT,environment.getProperty(prefix+"TIMEOUT_MS",Integer.class,20000),32000,8192,embedding?environment.getProperty("AI_EMBEDDING_DIMENSIONS",Integer.class,1024):null,Map.of()));
        var bindings=new HashMap<>(current.configuration().bindings());
        if(embedding)bindings.putIfAbsent(AiUsage.CATALOG_EMBEDDING,modelId);else{bindings.putIfAbsent(AiUsage.PROCUREMENT_PARSE,modelId);bindings.putIfAbsent(AiUsage.SKU_RERANK,modelId);}
        return configuration.save(new AiConfigSaveCommand(expectedRevision,providers,models,bindings));
    }
}
