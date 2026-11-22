package com.aryn.cloud.upms.api.entity.ai;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
@Getter @Setter @TableName("ai_usage_budget")
public class AiUsageBudget implements java.io.Serializable {
    @TableId(type=IdType.INPUT) private String id;
    private String tenantId;
    private String usageCode;
    @TableLogic private String delFlag;
    private String createBy;
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
