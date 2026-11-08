package com.aryn.cloud.pay.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 支付/退款事件 outbox。与资金状态在同一事务写入，由重投任务异步投递。
 */
@Data
@Schema(description = "支付事件 outbox")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "pay_event_outbox")
public class PayEventOutbox extends Model<PayEventOutbox> {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "事件唯一ID，消费端幂等键")
	private String eventId;

	@Schema(description = "事件类型：PAY_SUCCESS/REFUND_SUCCESS")
	private String eventType;

	@Schema(description = "业务键：订单号/退款单号")
	private String businessKey;

	@Schema(description = "投递 Topic")
	private String topic;

	@Schema(description = "非敏感消息负载 JSON")
	private String payload;

	@Schema(description = "状态：0.待投递；1.已投递；2.死信；3.投递中")
	private String status;

	@Schema(description = "已尝试次数")
	private Integer attempts;

	@Schema(description = "下次重试时间")
	private LocalDateTime nextRetryTime;

	@Schema(description = "最近失败原因，截断保存")
	private String lastError;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.UPDATE)
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	private String delFlag;

	private String tenantId;

}
