package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 持久采购记录；写入仅通过受控服务命令。 */
@Data
@TableName("procurement_draft_row")
public class ProcurementDraftRow implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String draftId;
    private String batchId;
    private String sourcePosition;
    private String sourceSheet;
    private Integer sourceRow;
    private Integer sourceOffset;
    private String rawText;
    private String rawName;
    private BigDecimal rawQuantity;
    private String rawUnit;
    private String rawSpec;
    private String rawCode;
    private String rawOwner;
    private String parseStatus;
    private String matchStatus;
    private String unitStatus;
    private String quantityStatus;
    private String reviewStatus;
    private String candidatesJson;
    private String matchedSkuId;
    private String selectedSkuId;
    private BigDecimal normalizedBaseQuantity;
    private Integer suggestedSalesQuantity;
    private Integer confirmedSalesQuantity;
    private BigDecimal excessBaseQuantity;
    private Long measurementRevision;
    private String decisionJson;
    private String reviewActor;
    private String reviewReason;
    private Long revision;
    private Integer parseRevision;
    private String tenantId;
    private String createBy;
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private String delFlag;
}
