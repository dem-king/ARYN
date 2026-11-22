package com.aryn.cloud.upms.api.entity.ai;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@Getter @Setter @TableName("ai_call_log")
public class AiCallLog implements java.io.Serializable {
    @TableId(type=IdType.INPUT) private String id;
    private String tenantId;
    @TableLogic private String delFlag;
    private String createBy;
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String usageCode;
    private Long reservedTokens;
    private String taskId;
    private String versionId;
    private String providerId;
    private String modelId;
    private String result;
    private Long totalTokens;
    private Long elapsedMillis;
    private java.time.LocalDateTime leaseUntil;
}
