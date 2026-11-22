package com.aryn.cloud.upms.api.remote;
import com.aryn.cloud.upms.api.dto.ai.*;
/** 凭证仅通过此受控内部契约传递；不得做HTTP代理或返回任意配置实体。 */
public interface RemoteAiConfigService {
    AiResolvedSnapshot resolve(AiRuntimeRequest request);
    String currentVersion(AiRuntimeRequest request);
    void checkRuntimeState(AiRuntimeRequest request);
    boolean beginCall(AiRuntimeRequest request);
    void reportUsage(AiUsageReport report);
}
