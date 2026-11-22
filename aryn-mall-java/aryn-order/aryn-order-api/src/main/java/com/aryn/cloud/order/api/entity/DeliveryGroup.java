package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 稳定合送业务组；与拣货波次、司机出车单分别建模。 */
@Data
@TableName("delivery_group")
public class DeliveryGroup implements Serializable {
    @TableField(exist = false)
    private Long orderCount;
    @TableField(exist = false)
    private Long unassignedCount;
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String warehouseId;
    private String vesselId;
    private String vesselCallId;
    private String portCode;
    private Integer windowSequence;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private LocalDateTime cutoff;
    private Integer maxOrders;
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
