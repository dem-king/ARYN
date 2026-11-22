package com.aryn.cloud.product.api.entity;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@Getter @Setter @TableName("catalog_vector_item")
public class CatalogVectorItem implements java.io.Serializable {
    @TableId(type=IdType.INPUT) private String id;
    private String tenantId;
    @TableLogic private String delFlag;
    private String createBy;
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String generationId;
    private String skuId;
    private String sourceName;
    private String vectorJson;
}
