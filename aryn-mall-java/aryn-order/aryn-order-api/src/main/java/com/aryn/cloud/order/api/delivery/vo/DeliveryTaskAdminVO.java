package com.aryn.cloud.order.api.delivery.vo;

import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTaskLog;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryEvidence;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** 管理端商城配送任务视图。 */
@Data
public class DeliveryTaskAdminVO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String id;
	private String taskNo;
	private String orderId;
	private String orderNo;
	private String status;
	private String assigneeId;
	private String assigneeName;
	private String assigneeMobile;
	private Integer attemptNo;
	private Integer version;
	private String exceptionCode;
	private String exceptionSummary;
	private String remark;
	private LocalDateTime pickingStartedAt;
	private LocalDateTime pickedUpAt;
	private LocalDateTime deliveredAt;
	private LocalDateTime returnPendingAt;
	private LocalDateTime returnedAt;
	private LocalDateTime closedAt;
	private LocalDateTime createTime;
	private LocalDateTime updateTime;
	private List<OrderDeliveryTaskLog> logs;
	private List<OrderDeliveryEvidence> evidences;

}
