package com.aryn.cloud.upms.api.client;
import com.aryn.cloud.upms.api.remote.RemoteAiConfigService;
import com.aryn.cloud.upms.api.dto.ai.*;
import com.aryn.cloud.common.ai.contract.*;
import com.aryn.cloud.common.ai.client.AiProtocolClient;
import com.aryn.cloud.common.ai.protocol.AiEndpointPolicy;
import com.aryn.cloud.common.ai.error.*;
import java.time.Instant;
import java.util.*;
/** 无全局active缓存；每个Session固定快照，外呼前向权威服务校验停用/预算。 */
public final class AiRuntimeClient {
    private final RemoteAiConfigService remote;
    private final String caller;
    private final String token;
    private final AiProtocolClient protocol;
    public AiRuntimeClient(RemoteAiConfigService remote,String caller,String token,Set<String> allowedHosts) {
        this.remote=remote;this.caller=caller;this.token=token;this.protocol=new AiProtocolClient(new AiEndpointPolicy(allowedHosts),4194304);
    }
    public Session open(String tenant,String task,AiUsage usage) {
        var context=new AiCallContext(UUID.randomUUID().toString(),task,tenant,usage);
        var request=signed("resolve",context,null,null,0);
        return new Session(context,remote.resolve(request));
    }
    public Session openVersion(String tenant,String task,AiUsage usage,String version) {
        var context=new AiCallContext(UUID.randomUUID().toString(),task,tenant,usage);
        return new Session(context,remote.resolve(signed("resolve",context,version,null,0)));
    }
    public final class Session {
        private final AiCallContext task;
        private final AiResolvedSnapshot resolved;
        private Session(AiCallContext task,AiResolvedSnapshot resolved){this.task=task;this.resolved=resolved;}
        public AiModelSnapshot model(){return resolved.model();}
        public AiResolvedSnapshot snapshot(){return resolved;}
        public AiTextResult text(String system,String input,Set<String> fields) {
            AiCallContext call=begin();
            try { var result=protocol.text(model(),call,system,input,fields);report(call,"SUCCESS",result.totalTokens(),result.elapsedMillis());return result; }
            catch(AiCallException ex){report(call,ex.code().name(),null,0);throw ex;}
            catch(RuntimeException ex){report(call,"INVALID_RESULT",null,0);throw ex;}
        }
        public AiEmbeddingResult embed(List<String> inputs) {
            AiCallContext call=begin();
            try{var result=protocol.embed(model(),call,inputs);report(call,"SUCCESS",result.totalTokens(),result.elapsedMillis());return result;}
            catch(AiCallException ex){report(call,ex.code().name(),null,0);throw ex;}
            catch(RuntimeException ex){report(call,"INVALID_RESULT",null,0);throw ex;}
        }
        private AiCallContext begin() {
            var context=new AiCallContext(UUID.randomUUID().toString(),task.taskId(),task.tenantId(),task.usage());
            if(!remote.beginCall(signed("begin",context,model().configVersion(),model().providerId(),resolved.emergencyEpoch()))) throw new AiCallException(AiErrorCode.DISABLED);
            remote.checkRuntimeState(signed("check",context,model().configVersion(),model().providerId(),resolved.emergencyEpoch()));
            return context;
        }
        private void report(AiCallContext context,String result,Long tokens,long elapsed) {
            var unsigned=signed("placeholder",context,model().configVersion(),model().providerId(),resolved.emergencyEpoch());
            var report=new AiUsageReport(unsigned,result,tokens,elapsed);
            var signed=signed(AiRuntimeSignatures.reportAction(report),context,model().configVersion(),model().providerId(),resolved.emergencyEpoch());
            // 记账失败保留RUNNING预留额度，不再重发模型请求；由运营可见的lease状态恢复。
            try{remote.reportUsage(new AiUsageReport(signed,result,tokens,elapsed));}catch(RuntimeException ignored){ }
        }
    }
    private AiRuntimeRequest signed(String action,AiCallContext context,String version,String provider,long epoch) {
        var unsigned=new AiRuntimeRequest(context,caller,Instant.now().getEpochSecond(),"",version,provider,epoch);
        return new AiRuntimeRequest(context,caller,unsigned.issuedAt(),AiRuntimeSignatures.sign(action,unsigned,token),version,provider,epoch);
    }
}
