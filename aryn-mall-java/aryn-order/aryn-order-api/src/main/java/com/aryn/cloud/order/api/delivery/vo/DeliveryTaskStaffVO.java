package com.aryn.cloud.order.api.delivery.vo;

import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryEvidence;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTaskItem;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** 配送员任务视图。 */
@Data
public class DeliveryTaskStaffVO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String id;
	private String taskNo;
	private String orderId;
	private String orderNo;
	private String status;
	private Integer attemptNo;
	private Integer version;
	private String recipientName;
	private String recipientPhone;
	private String recipientProvince;
	private String recipientCity;
	private String recipientArea;
	private String recipientAddress;
	private String exceptionCode;
	private String exceptionSummary;
	private LocalDateTime createTime;
	private LocalDateTime updateTime;
	private List<OrderDeliveryTaskItem> items;
	private List<OrderDeliveryEvidence> evidence;

}
