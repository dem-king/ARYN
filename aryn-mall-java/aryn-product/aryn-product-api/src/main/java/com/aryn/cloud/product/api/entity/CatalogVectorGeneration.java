package com.aryn.cloud.product.api.entity;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@Getter @Setter @TableName("catalog_vector_generation")
public class CatalogVectorGeneration implements java.io.Serializable {
    @TableId(type=IdType.INPUT) private String id;
    private String tenantId;
    @TableLogic private String delFlag;
    private String createBy;
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String configVersion;
    private String modelFingerprint;
    private Integer dimension;
    private String catalogFingerprint;
    private String status;
    private String cursorId;
    private Integer indexedRows;
    private Integer expectedRows;
    private String leaseToken;
    private LocalDateTime leaseUntil;
    private String errorCode;
}
