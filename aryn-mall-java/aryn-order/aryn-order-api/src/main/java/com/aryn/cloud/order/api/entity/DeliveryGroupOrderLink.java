package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 订单归组关系；同租户每单只有一个当前关系，移组历史保留在操作记录。 */
@Data
@TableName("delivery_group_order_link")
public class DeliveryGroupOrderLink implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String groupId;
    private String orderId;
    private String warehouseId;
    private String waveId;
    private String taskId;
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
