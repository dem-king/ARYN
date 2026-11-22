package com.aryn.cloud.product.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("product_sku_identifier")
public class SkuIdentifier implements Serializable {
    @TableId(type = IdType.ASSIGN_UUID) private String id;
    private String skuId;
    private String identifierType;
    private String displayValue;
    private String normalizedValue;
    private String status;
    private String source;
    private String reviewBy;
    private LocalDateTime reviewTime;
    @Version private Long revision;
    private String tenantId;
    @TableLogic private String delFlag;
    private String createBy;
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
