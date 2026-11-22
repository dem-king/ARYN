package com.aryn.cloud.upms.api.dto.ai;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
/** 内部调用授权，不复用用户会话，不写入HTTP配置响应。每个服务独立部署token。 */
public final class AiRuntimeSignatures {
    private AiRuntimeSignatures() { }
    public static String sign(String action,AiRuntimeRequest request,String token) {
        if(token==null || token.length()<32) throw new IllegalStateException("AI 内部调用凭证未配置（至少32字符）");
        try {
            var c=request.context();
            String[] fields={action,request.caller(),Long.toString(request.issuedAt()),c.tenantId(),c.taskId(),c.callId(),c.usage().name(),request.version(),request.providerId(),Long.toString(request.emergencyEpoch())};
            StringBuilder message=new StringBuilder();
            for(String field:fields) { String value=field==null?"":field;message.append(value.length()).append(':').append(value); }
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.toString().getBytes(StandardCharsets.UTF_8)));
        }catch(java.security.GeneralSecurityException ex){throw new IllegalStateException("AI 内部签名失败");}
    }
    public static String reportAction(AiUsageReport report) { return "report|"+report.result()+"|"+report.totalTokens()+"|"+report.elapsedMillis(); }
}
