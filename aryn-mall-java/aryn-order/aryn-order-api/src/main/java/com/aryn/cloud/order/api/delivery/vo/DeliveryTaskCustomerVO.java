package com.aryn.cloud.order.api.delivery.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** 客户侧配送进度，只暴露履约状态和公开时间点。 */
@Data
public class DeliveryTaskCustomerVO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String id;
	private String orderId;
	private String status;
	private String assigneeName;
	private LocalDateTime createTime;
	private LocalDateTime updateTime;
	private LocalDateTime pickingStartedAt;
	private LocalDateTime pickedUpAt;
	private LocalDateTime deliveredAt;
	private LocalDateTime returnPendingAt;
	private LocalDateTime returnedAt;
	private LocalDateTime closedAt;
	private List<DeliveryEvidenceCustomerVO> evidences;
}
