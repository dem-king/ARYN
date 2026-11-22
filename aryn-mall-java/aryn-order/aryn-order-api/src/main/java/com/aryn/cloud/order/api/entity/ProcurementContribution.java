package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 持久采购记录；写入仅通过受控服务命令。 */
@Data
@TableName("procurement_contribution")
public class ProcurementContribution implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String draftId;
    private String batchId;
    private String sourceRowId;
    private String targetType;
    private String targetId;
    private String targetLineId;
    private String ownerUserId;
    private String skuId;
    private Integer quantity;
    private String status;
    private String origin;
    private Long revision;
    private String tenantId;
    private String createBy;
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private String delFlag;
}
