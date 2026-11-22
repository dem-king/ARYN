package com.aryn.cloud.upms.api.entity.ai;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@Getter @Setter @TableName("ai_config_operation")
public class AiConfigOperation implements java.io.Serializable {
    @TableId(type=IdType.INPUT) private String id;
    private String tenantId;
    @TableLogic private String delFlag;
    private String createBy;
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String operation;
    private String versionId;
    private String previousVersion;
}
