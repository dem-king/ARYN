package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 持久采购记录；写入仅通过受控服务命令。 */
@Data
@TableName("procurement_draft")
public class ProcurementDraft implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String ownerUserId;
    private String purchaseScene;
    private String buyerOrgId;
    private String vesselId;
    private String vesselCallId;
    private String title;
    private String clientRequestId;
    private String contextFingerprint;
    private String status;
    private Long revision;
    private String tenantId;
    private String createBy;
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private String delFlag;
}
