package com.aryn.cloud.upms.api.entity.ai;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@Getter @Setter @TableName("ai_capability_test")
public class AiCapabilityTest implements java.io.Serializable {
    @TableId(type=IdType.INPUT) private String id;
    private String tenantId;
    @TableLogic private String delFlag;
    private String createBy;
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String versionId;
    private String modelId;
    private Boolean passed;
    private String status;
}
