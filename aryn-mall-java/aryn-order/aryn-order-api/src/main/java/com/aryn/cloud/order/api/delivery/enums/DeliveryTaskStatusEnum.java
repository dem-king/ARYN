package com.aryn.cloud.order.api.delivery.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商城配送任务状态。
 */
@Getter
@RequiredArgsConstructor
public enum DeliveryTaskStatusEnum {

	WAITING_ASSIGNMENT("待派单"),
	ASSIGNED("已派单"),
	PICKING("配货中"),
	DELIVERING("配送中"),
	DELIVERED("已送达"),
	EXCEPTION("配送异常"),
	RETURN_PENDING("待退回"),
	CLOSED("已关闭");

	private final String description;

}
