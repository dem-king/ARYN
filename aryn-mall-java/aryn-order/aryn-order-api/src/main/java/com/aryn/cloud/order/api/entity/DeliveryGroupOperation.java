package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 不可变操作与事务outbox；发送失败保留并重试，事件ID供消息端幂等。 */
@Data
@TableName("delivery_group_operation")
public class DeliveryGroupOperation implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String groupId;
    private String requestId;
    private String fingerprint;
    private String action;
    private String actorId;
    private String reason;
    private String requestJson;
    private String resultJson;
    private String notificationJson;
    private String outboxStatus;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private String leaseToken;
    private LocalDateTime leaseUntil;
    private String tenantId;
    private String createBy;
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private String delFlag;
}
