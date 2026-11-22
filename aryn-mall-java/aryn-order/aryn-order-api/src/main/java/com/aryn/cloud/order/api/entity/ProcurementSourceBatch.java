package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 持久采购记录；写入仅通过受控服务命令。 */
@Data
@TableName("procurement_source_batch")
public class ProcurementSourceBatch implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String draftId;
    private String operatorUserId;
    private String inputType;
    private String clientRequestId;
    private String inputDigest;
    private Boolean explicitNew;
    private String inputName;
    private String inputText;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private byte[] inputBytes;
    private String templateVersion;
    private String configVersion;
    private String parserSource;
    private Integer parseRevision;
    private String status;
    private Integer totalRows;
    private Integer completedRows;
    private Integer sourceCursor;
    private Integer attempt;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String leaseToken;
    private LocalDateTime leaseUntil;
    private LocalDateTime heartbeat;
    private LocalDateTime nextRetryAt;
    private String errorCode;
    private Long revision;
    private String confirmedRequestId;
    private String confirmationJson;
    private String replacedByBatchId;
    private String tenantId;
    private String createBy;
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private String delFlag;
}
