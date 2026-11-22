package com.aryn.cloud.common.ai.contract;
import java.io.Serializable;
/** 仅供后端受控RPC序列化，HTTP接口禁止返回本类型或运行快照。 */
public record AiSecret(String value) implements Serializable {
    public AiSecret {
        if(value==null || value.isBlank() || value.contains("\n") || value.contains("\r")) throw new IllegalArgumentException("AI 凭证无效");
    }
    public String reveal(){return value;}
    @Override public String toString(){return "[REDACTED]";}
}
